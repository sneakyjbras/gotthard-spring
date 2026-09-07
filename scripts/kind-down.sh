#!/usr/bin/env bash
#
# gotthard-spring — tear down the local kind cluster scripts/kind-up.sh
# creates. Idempotent: safe to run whether or not the cluster exists.
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &>/dev/null && pwd)"
REPO_DIR="$(cd -- "$SCRIPT_DIR/.." &>/dev/null && pwd)"
CLUSTER_NAME="gotthard"
KUBECONFIG_PATH="$REPO_DIR/.kube/kind-gotthard.config"

command -v kind >/dev/null 2>&1 || {
  echo "ERROR: kind is required and not on PATH" >&2
  exit 1
}

if kind get clusters 2>/dev/null | grep -qx "$CLUSTER_NAME"; then
  echo "==> Deleting kind cluster '$CLUSTER_NAME'"
  kind delete cluster --name "$CLUSTER_NAME" --kubeconfig "$KUBECONFIG_PATH"
else
  echo "==> kind cluster '$CLUSTER_NAME' does not exist — nothing to delete"
fi

rm -f "$KUBECONFIG_PATH"

cat <<EOF

==> Done. The host's default ~/.kube/config was never touched (see
    argocd/README.md) so there is nothing to restore there.

    Locally built images are still in the host's Docker image cache —
    'docker rmi ghcr.io/sneakyjbras/gotthard-{backend,frontend,docs}:local'
    if you want those gone too; scripts/kind-up.sh rebuilds them from
    scratch either way, so leaving them costs nothing but disk space.
EOF
