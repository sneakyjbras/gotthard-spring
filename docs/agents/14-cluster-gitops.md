# Agent 14 — kind cluster, ArgoCD and observability

**Model:** Sonnet · **Wave:** 4.2 · **Scope:** `scripts/`, `argocd/`, `chart/templates/`

Sonnet, and serial rather than parallel: this depends on the chart existing, and every
layer of it depends on the one below. Infrastructure does not fan out the way feature
work does.

## Instructions given
> A scripted kind cluster, the app-of-apps pattern from `av-tools-infra` reconciling the
> chart for real, and kube-prometheus-stack scraping the Actuator endpoint with the
> already-authored dashboard provisioned as a ConfigMap. Bring it up and prove it syncs;
> report honestly if it does not.
