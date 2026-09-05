#!/usr/bin/env bash
#
# gotthard-spring — the only script you need.
#
# The general case takes no arguments. Everything else is a corner case.
set -euo pipefail

readonly ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly APP_URL="http://localhost:8080"

usage() {
    cat <<'EOF'
gotthard-spring — customer activity analytics

USAGE
    ./start.sh              Start everything. This is the one you want.
    ./start.sh --down       Stop and remove containers (data is kept).
    ./start.sh --reset      Stop and delete the database volume too.
    ./start.sh --help       This message.

LLM
    If ANTHROPIC_API_KEY is set, analyses run against Claude.
    If it is not, a deterministic stub is used instead and the app
    says so on screen. Nothing needs configuring either way.
EOF
}

die() { printf '\n  %s\n\n' "$*" >&2; exit 1; }
step() { printf '\n  \033[1m%s\033[0m\n' "$*"; }
info() { printf '    %s\n' "$*"; }

require_tooling() {
    command -v docker >/dev/null || die "docker is not installed."
    docker compose version >/dev/null 2>&1 \
        || die "the docker compose plugin is missing. On Arch/Manjaro: sudo pacman -S docker-compose"
    docker info >/dev/null 2>&1 || die "the docker daemon is not running."
}

start_database() {
    step "Starting PostgreSQL"
    docker compose -f "$ROOT/docker-compose.yml" up -d postgres
    info "waiting for it to accept connections..."
    local waited=0
    until docker compose -f "$ROOT/docker-compose.yml" exec -T postgres \
              pg_isready -U gotthard -d gotthard >/dev/null 2>&1; do
        sleep 1
        waited=$((waited + 1))
        [ "$waited" -lt 60 ] || die "PostgreSQL did not come up within 60s."
    done
    info "ready on localhost:5432"
}

report_llm_mode() {
    if [ -n "${ANTHROPIC_API_KEY:-}" ]; then
        info "ANTHROPIC_API_KEY found — analyses will call Claude."
    else
        info "no ANTHROPIC_API_KEY — using the deterministic stub."
    fi
}

start_backend() {
    step "Starting the backend"
    report_llm_mode
    info "migrations run on boot; first start compiles, so give it a minute."
    info "when it is up: $APP_URL"
    exec "$ROOT/backend/gradlew" -p "$ROOT/backend" bootRun
}

take_everything_down() {
    step "Stopping"
    docker compose -f "$ROOT/docker-compose.yml" down
    info "containers removed, database volume kept."
}

reset_everything() {
    step "Resetting"
    docker compose -f "$ROOT/docker-compose.yml" down -v
    info "containers and database volume removed."
}

main() {
    case "${1:-}" in
        "")             require_tooling; start_database; start_backend ;;
        --down)         take_everything_down ;;
        --reset)        reset_everything ;;
        -h|--help)      usage ;;
        *)              usage; die "unknown option: $1" ;;
    esac
}

main "$@"
