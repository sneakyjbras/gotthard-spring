#!/usr/bin/env bash
#
# One command for the demo. Brings up everything and tells you what to say.
#
# Run this BEFORE the call, not during it — the first run builds three images
# and can take ten minutes. A second run reuses what is already there.
set -euo pipefail

readonly ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

say()  { printf '\n\033[1m  %s\033[0m\n' "$*"; }
note() { printf '     %s\n' "$*"; }
rule() { printf '\n  %s\n' "────────────────────────────────────────────────────────────"; }

usage() {
    cat <<'EOF'
  ./demo.sh            Everything: cluster, GitOps, monitoring, app.
  ./demo.sh --local    Just the app on Docker, no Kubernetes. Faster.
  ./demo.sh --down     Tear it all down.
  ./demo.sh --help     This.
EOF
}

# ---------------------------------------------------------------- the general case
full_demo() {
    say "1/3  Building and starting the cluster"
    note "kind creates a single-node Kubernetes cluster. Three images are built"
    note "locally and loaded into it — the registry is private, so nothing is pulled."
    note "This is the slow part. Talk over it."
    "$ROOT/scripts/kind-up.sh"

    say "2/3  What ArgoCD did"
    note "You applied one Application. It created the others, and they reconciled"
    note "the Helm chart and the monitoring stack from git. Nothing was applied by hand."
    export KUBECONFIG="$ROOT/.kube/kind-gotthard.config"
    kubectl get applications -n argocd

    say "3/3  What is running"
    kubectl get pods -n gotthard --no-headers | awk '{printf "     %-42s %s\n", $1, $3}'
    kubectl get pods -n monitoring --no-headers | awk '{printf "     %-42s %s\n", $1, $3}'

    rule
    say "Open these"
    note "http://gotthard.localhost/     the operator console"
    note "http://argocd.localhost/       three applications, all green"
    note "http://grafana.localhost/      golden signals and the alert rules"
    note "http://docs.localhost/         the documentation site"
    demo_script
}

# ---------------------------------------------------------------- the fast path
local_demo() {
    say "Starting the app on Docker — no Kubernetes"
    note "Postgres, migrations, then the backend. Use this if the cluster misbehaves."
    note "Run 'npm run dev' in frontend/ in another terminal for the UI."
    exec "$ROOT/start.sh"
}

# ---------------------------------------------------------------- what to say
demo_script() {
    rule
    say "The five minutes that matter"
    cat <<'EOF'
     1. Log in as e.rossi / Operator-Demo-2026

     2. Search CH-7002-4471 — Livia Baumann. LOW, nothing fired.
        Say: "I am showing you the clean one first. Anyone can build
        something that flags everybody."

     3. Search CH-7002-4488 — Sandra Wyss. Scroll her payments.
        Five payments, 9,300 to 9,750, same account in Myanmar, six days.
        Say: "No single one of those is unusual. The pattern only exists
        across the window."

     4. Point at the score: CRITICAL, 76, two rules named with their
        conditions in plain English.
        Say: "The rules decided that. No model was involved yet."

     5. Run the AI analysis.
        Say: "This is the only place the model is used. It explains what
        the rules found, against policy it was shown because those rules
        fired — so every citation traces back to a rule."

        If the two risk levels differ, stop and point at it. That is the
        audit signal, and it is a feature.

     Full runbook, with the questions they will ask: docs/demo.md
EOF
    printf '\n'
}

tear_down() {
    say "Tearing down"
    "$ROOT/scripts/kind-down.sh" || true
    "$ROOT/start.sh" --down || true
    note "Gone. ./demo.sh brings it back."
}

main() {
    case "${1:-}" in
        "")         full_demo ;;
        --local)    local_demo ;;
        --down)     tear_down ;;
        -h|--help)  usage ;;
        *)          usage; exit 1 ;;
    esac
}

main "$@"
