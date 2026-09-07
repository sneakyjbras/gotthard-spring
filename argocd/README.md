# GitOps on a local cluster

`scripts/kind-up.sh` builds the images, creates a single-node kind cluster,
installs ingress-nginx and ArgoCD, and hands the rest to ArgoCD. `kind-down.sh`
removes it. Both are idempotent.

## Verified state

Run on 2026-09-07 against a cluster brought up from scratch:

```
NAME                    SYNC STATUS   HEALTH STATUS
gotthard-kind           Synced        Healthy
gotthard-root           Synced        Healthy
kube-prometheus-stack   Synced        Healthy
```

Four pods in `gotthard` (backend, frontend, docs, postgres), four in
`monitoring` (Grafana, Prometheus, the operator, kube-state-metrics), ten
`monitoring.coreos.com` CRDs, and the backend's ServiceMonitor registered so
Prometheus scrapes `/actuator/prometheus`.

## What is automated, and what a person still does

ArgoCD reconciles everything under `chart/` and the monitoring stack from git,
continuously. The root Application creates the others; nothing is applied by
hand after the script runs.

Two things are **not** what a production setup would do, and it is worth being
plain about them:

**The repository is read from a bind mount, not over the network.** The kind node
mounts the working tree at `/repo-source` and `argocd-repo-server` is patched to
read `file:///repo-source`. This is what makes GitOps work here for a private
repository with no credentials. A real cluster would use a deploy key or a token
and pull over HTTPS; the manifests would otherwise be identical.

**The Prometheus Operator's CRDs are installed out of band.** Several of them
exceed the 262144-byte ceiling Kubernetes places on annotations, and a
client-side apply writes the whole manifest into `last-applied-configuration`.
The script applies them with `kubectl apply --server-side`, and the Application
sets `skipCrds: true` so Helm does not try to manage them as well. This is the
documented workaround for kube-prometheus-stack under ArgoCD, not a local
shortcut.

## Reaching things

```
gotthard-spring   http://gotthard.localhost/
docs              http://docs.localhost/
Grafana           http://grafana.localhost/
ArgoCD            http://argocd.localhost/
```

`*.localhost` resolves to 127.0.0.1 in modern browsers and in glibc. If yours
does not, port-forward instead — `chart/templates/NOTES.txt` lists the services.

ArgoCD's initial admin password:

```
kubectl -n argocd get secret argocd-initial-admin-secret \
  -o jsonpath='{.data.password}' | base64 -d
```

Grafana's is set in `argocd/kube-prometheus-stack.yaml`. Both are local-only
demo credentials on a throwaway cluster.

## What was learned bringing this up

Four failures, none of which `helm lint` or `helm template` can catch, because
each only exists when something tries to run:

1. **`targetRevision` pointed at a branch that had been merged and deleted.**
   The root Application was Synced and Healthy while both children sat OutOfSync
   and Missing — which is exactly what a stale revision looks like, since the
   root's job is only to create the children.

2. **ArgoCD's own install needs `--server-side`.** The ApplicationSet CRD is
   larger than the annotation ceiling, so a plain `kubectl apply -f` fails on a
   fresh install.

3. **The AppProject did not permit `kube-system`**, where kube-prometheus-stack
   places kube-state-metrics and the node exporter. Every sync failed validation
   before creating anything.

4. **The chart's ServiceMonitor deadlocked against the monitoring stack.** It is
   a `monitoring.coreos.com/v1` resource, so the application could not install
   until the operator had — and the operator is deployed by this same GitOps
   setup. The template is now guarded on the CRD being present, which is what a
   chart shipping an optional ServiceMonitor should do regardless: it renders
   nothing on a cluster without an operator, and appears once there is one.
