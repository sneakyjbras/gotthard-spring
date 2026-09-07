#!/usr/bin/env bash
#
# gotthard-spring — local kind bring-up, one command (post-submission bonus,
# see CLAUDE.md). Creates a single-node kind cluster, gets ArgoCD running on
# it for real, and has ArgoCD reconcile the Helm chart plus
# kube-prometheus-stack. See argocd/README.md for what this does and does
# not prove, and for exactly what a real (non-local) cluster would need
# instead of the two shortcuts this script takes.
#
# Images: ghcr.io/sneakyjbras/gotthard-{backend,frontend,docs} are PRIVATE
# packages (private source repo). Pulling them into a throwaway local
# cluster would mean putting a registry credential somewhere just to throw
# it away with the cluster. Instead this script builds the three images
# locally from the same Dockerfiles CI uses, then `kind load docker-image`s
# them straight into the node's containerd — no registry round trip, no
# credential, and it always reflects the working tree rather than whatever
# happens to be tagged `latest` on ghcr.io. chart/values-kind.yaml sets
# pullPolicy: Never to match (nothing to pull; a kubelet pull attempt would
# just fail against a registry it has no credential for anyway).
#
# GitOps source: ArgoCD needs a git source it can clone. The real source repo
# (github.com/sneakyjbras/gotthard-spring) is private, so rather than
# smuggling a GitHub PAT into a disposable local cluster, this script mounts
# THIS CHECKOUT's git object database into the kind node (kind's own
# `extraMounts`) and gives ArgoCD a `file:///repo-source` source pointing at
# it (wired up by argocd/repo-server-local-source-patch.yaml). Because git
# worktrees share one object database and one set of refs, this resolves the
# branch under test correctly even though these commits live on a worktree
# branch, not on whatever branch happens to be checked out in the primary
# working copy this script mounts. argocd/README.md documents precisely what
# a *real* cluster would need instead (a registered deploy token / PAT).
#
# Idempotent: safe to re-run. Reuses an existing cluster, re-applies
# manifests, re-patches deployments (kubectl patch is itself idempotent —
# applying the same strategic-merge patch twice is a no-op the second time).
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &>/dev/null && pwd)"
REPO_DIR="$(cd -- "$SCRIPT_DIR/.." &>/dev/null && pwd)"

CLUSTER_NAME="gotthard"
NAMESPACE="gotthard"
ARGOCD_NS="argocd"
INGRESS_NS="ingress-nginx"
INGRESS_NGINX_MANIFEST="https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.15.1/deploy/static/provider/kind/deploy.yaml"
ARGOCD_MANIFEST="https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml"

# Own kubeconfig, never the default ~/.kube/config — that one's
# current-context points at a real cluster (see argocd/README.md). Every
# kubectl/helm/kind invocation below is explicit about this, on purpose.
mkdir -p "$REPO_DIR/.kube"
KUBECONFIG_PATH="$REPO_DIR/.kube/kind-gotthard.config"
export KUBECONFIG="$KUBECONFIG_PATH"

log() { printf '\n==> %s\n' "$*"; }
die() {
  printf '\nERROR: %s\n' "$*" >&2
  exit 1
}

# ---------------------------------------------------------------------------
log "Preflight"
for bin in docker kind kubectl helm git; do
  command -v "$bin" >/dev/null 2>&1 || die "$bin is required and not on PATH"
done
docker info >/dev/null 2>&1 || die "Docker daemon not reachable"

# The shared .git directory's parent is the checkout that actually has a
# real .git/ (not a worktree's gitdir pointer file) — see the header comment
# above for why this, not this worktree, is what gets mounted.
GIT_COMMON_DIR="$(git -C "$REPO_DIR" rev-parse --path-format=absolute --git-common-dir)"
MOUNT_SRC="$(dirname "$GIT_COMMON_DIR")"
BRANCH="$(git -C "$REPO_DIR" branch --show-current)"
[ -n "$BRANCH" ] || die "HEAD is detached — check out the branch this bonus work lives on first"
log "Local git source for ArgoCD: $MOUNT_SRC (branch '$BRANCH') -> /repo-source in the kind node"
if [ "$BRANCH" != "worktree-agent-a8418dd3dc3e40a01" ]; then
  echo "    The manifests track 'main'. ArgoCD reads the repository from a bind
    mount inside the kind node rather than over the network, so this works
    for a private repository with no credentials — and means the branch
    must actually exist locally."
  echo "    targetRevision: worktree-agent-a8418dd3dc3e40a01 — update both if this"
  echo "    branch has since been renamed or merged (see CLAUDE.md: DO NOT MERGE)."
fi

# ---------------------------------------------------------------------------
if kind get clusters 2>/dev/null | grep -qx "$CLUSTER_NAME"; then
  log "kind cluster '$CLUSTER_NAME' already exists — reusing it"
  kind export kubeconfig --name "$CLUSTER_NAME" --kubeconfig "$KUBECONFIG_PATH"
else
  log "Creating kind cluster '$CLUSTER_NAME' (single node)"
  KIND_CONFIG="$(mktemp)"
  trap 'rm -f "$KIND_CONFIG"' EXIT
  cat >"$KIND_CONFIG" <<EOF
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
nodes:
  - role: control-plane
    # /repo-source: this checkout's git object database, for ArgoCD's
    # file:// source (see this script's header).
    extraMounts:
      - hostPath: $MOUNT_SRC
        containerPath: /repo-source
        readOnly: true
    # 80/443 on the host -> ingress-nginx, per kind's own documented pattern
    # (https://kind.sigs.k8s.io/docs/user/ingress/) for a single-node
    # cluster: the node IS the one Ingress-ready node.
    extraPortMappings:
      - containerPort: 80
        hostPort: 80
        protocol: TCP
      - containerPort: 443
        hostPort: 443
        protocol: TCP
    kubeadmConfigPatches:
      - |
        kind: InitConfiguration
        nodeRegistration:
          kubeletExtraArgs:
            node-labels: "ingress-ready=true"
EOF
  kind create cluster --name "$CLUSTER_NAME" --config "$KIND_CONFIG" --kubeconfig "$KUBECONFIG_PATH"
  rm -f "$KIND_CONFIG"
  trap - EXIT
fi
kubectl cluster-info >/dev/null || die "cluster came up but is not reachable"

# ---------------------------------------------------------------------------
log "Building images locally (see this script's header — ghcr.io is private)"
docker build -q -t ghcr.io/sneakyjbras/gotthard-backend:local -f "$REPO_DIR/backend/Dockerfile" "$REPO_DIR/backend"
docker build -q -t ghcr.io/sneakyjbras/gotthard-frontend:local -f "$REPO_DIR/frontend/Dockerfile" "$REPO_DIR/frontend"
docker build -q -t ghcr.io/sneakyjbras/gotthard-docs:local -f "$REPO_DIR/docs/Dockerfile" -- "$REPO_DIR"

log "Loading images into the kind node's containerd"
kind load docker-image ghcr.io/sneakyjbras/gotthard-backend:local --name "$CLUSTER_NAME"
kind load docker-image ghcr.io/sneakyjbras/gotthard-frontend:local --name "$CLUSTER_NAME"
kind load docker-image ghcr.io/sneakyjbras/gotthard-docs:local --name "$CLUSTER_NAME"

# ---------------------------------------------------------------------------
log "Installing ingress-nginx (kind provider manifest)"
kubectl apply --server-side --force-conflicts -f "$INGRESS_NGINX_MANIFEST"
kubectl -n "$INGRESS_NS" wait --for=condition=available deployment/ingress-nginx-controller --timeout=180s

# ---------------------------------------------------------------------------
log "Installing ArgoCD"
kubectl get ns "$ARGOCD_NS" >/dev/null 2>&1 || kubectl create ns "$ARGOCD_NS"
# Server-side apply: the ApplicationSet CRD is larger than the 262144-byte
# ceiling on the last-applied-configuration annotation that client-side apply
# writes, so `kubectl apply -f` fails on a fresh install. Server-side apply
# keeps the managed-fields on the server instead of stuffing them into an
# annotation, and has no such limit.
kubectl apply --server-side --force-conflicts -n "$ARGOCD_NS" -f "$ARGOCD_MANIFEST"
kubectl -n "$ARGOCD_NS" rollout status deploy/argocd-repo-server --timeout=300s
kubectl -n "$ARGOCD_NS" rollout status deploy/argocd-server --timeout=300s
kubectl -n "$ARGOCD_NS" rollout status deploy/argocd-applicationset-controller --timeout=300s

log "Patching argocd-repo-server for the local file:// source"
kubectl -n "$ARGOCD_NS" patch deployment argocd-repo-server \
  --type strategic --patch-file "$REPO_DIR/argocd/repo-server-local-source-patch.yaml"
kubectl -n "$ARGOCD_NS" rollout status deploy/argocd-repo-server --timeout=180s
kubectl -n "$ARGOCD_NS" get deployment argocd-repo-server \
  -o jsonpath='{.spec.template.spec.containers[0].volumeMounts[*].name}' | grep -q repo-source \
  || die "repo-source volume mount did not land on argocd-repo-server — check argocd/repo-server-local-source-patch.yaml"

log "Patching argocd-server to serve plain HTTP behind ingress-nginx"
kubectl -n "$ARGOCD_NS" patch deployment argocd-server \
  --type strategic --patch-file "$REPO_DIR/argocd/argocd-server-insecure-patch.yaml"
kubectl -n "$ARGOCD_NS" rollout status deploy/argocd-server --timeout=180s
kubectl apply -f "$REPO_DIR/argocd/argocd-server-ingress.yaml"

# ---------------------------------------------------------------------------
# LOCAL DEMO ONLY. A real/shared cluster keeps this manual and out-of-band —
# see secrets/secret.example.yaml, which this script deliberately does not
# read from (it is a template for a human to fill in and apply by hand). The
# one-command requirement for THIS script is what justifies generating a
# throwaway credential here instead.
log "Creating the demo credentials Secret (local kind only, see comment above)"
kubectl get ns "$NAMESPACE" >/dev/null 2>&1 || kubectl create ns "$NAMESPACE"
kubectl -n "$NAMESPACE" create secret generic gotthard-secrets \
  --from-literal=POSTGRES_DB=gotthard \
  --from-literal=POSTGRES_USER=gotthard \
  --from-literal=POSTGRES_PASSWORD=gotthard-demo-password \
  --dry-run=client -o yaml | kubectl apply -f -
# ANTHROPIC_API_KEY is deliberately never set here — the intended reviewer
# path is the offline stub AI adapter (see chart/templates/NOTES.txt).

# ---------------------------------------------------------------------------
# The stack's own CreateNamespace ran after its CRDs, so a failure there left
# the namespace missing and every later task blaming that instead of the CRDs.
# Creating it up front removes the misleading second error.
kubectl create namespace monitoring --dry-run=client -o yaml | kubectl apply -f - >/dev/null

log "Applying the AppProject and the root Application"
kubectl apply -n "$ARGOCD_NS" -f "$REPO_DIR/argocd/appproject.yaml"
kubectl apply -n "$ARGOCD_NS" -f "$REPO_DIR/argocd/app-of-apps.yaml"

# ---------------------------------------------------------------------------
RECONCILED=false
log "Waiting for ArgoCD to reconcile everything (kube-prometheus-stack's CRDs land first, then the chart — can take several minutes on a cold cluster)"
EXPECTED_APPS="gotthard-root kube-prometheus-stack gotthard-kind"
TIMEOUT_S=900
INTERVAL_S=10
elapsed=0
while true; do
  statuses="$(kubectl get applications -n "$ARGOCD_NS" \
    -o jsonpath='{range .items[*]}{.metadata.name}={.status.sync.status}/{.status.health.status}{"\n"}{end}' 2>/dev/null || true)"
  echo "    [${elapsed}s]"
  printf '%s\n' "$statuses" | sed 's/^/      /'

  all_present=true
  all_healthy=true
  for app in $EXPECTED_APPS; do
    entry="$(printf '%s\n' "$statuses" | grep "^${app}=" || true)"
    if [ -z "$entry" ]; then
      all_present=false
    elif [ "$entry" != "${app}=Synced/Healthy" ]; then
      all_healthy=false
    fi
  done

  if $all_present && $all_healthy; then
    log "All Applications Synced/Healthy"
    RECONCILED=true
    break
  fi

  elapsed=$((elapsed + INTERVAL_S))
  if [ "$elapsed" -ge "$TIMEOUT_S" ]; then
    echo "    TIMED OUT after ${TIMEOUT_S}s — printing current state and continuing (see argocd/README.md)"
    RECONCILED=false
    break
  fi
  sleep "$INTERVAL_S"
done

# ---------------------------------------------------------------------------
log "ArgoCD Applications"
kubectl get applications -n "$ARGOCD_NS" -o wide || true

log "Pods across the cluster"
kubectl get pods -A || true

cat <<EOF

==> Done.

    kubeconfig: $KUBECONFIG_PATH
    (export KUBECONFIG=$KUBECONFIG_PATH, or pass --kubeconfig, for further kubectl/helm/argocd commands)

    Reachable via ingress-nginx (http, *.localhost resolves to 127.0.0.1 in
    every modern browser and in curl/glibc on this machine — see
    argocd/README.md if it doesn't resolve for you):

      gotthard-spring   http://gotthard.localhost/
      docs site         http://docs.localhost/
      Grafana           http://grafana.localhost/          (admin / see argocd/README.md)
      ArgoCD UI         http://argocd.localhost/            (admin / see argocd/README.md)

    If ingress doesn't resolve, port-forward instead (see chart/templates/NOTES.txt
    and argocd/README.md for the full set, including Prometheus and Grafana).

    Tear down with scripts/kind-down.sh.
EOF

# ---------------------------------------------------------------------------
# The URLs above are printed either way, because knowing what did come up is
# useful when something did not. The exit status is what a caller reads, so it
# reports reconciliation rather than "the script reached the end".
if [ "${RECONCILED:-false}" != "true" ]; then
  echo
  echo "    NOT every Application reached Synced/Healthy. The URLs above may not"
  echo "    resolve. Check: kubectl get applications -n argocd"
  exit 1
fi
