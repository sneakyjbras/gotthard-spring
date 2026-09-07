# Agent 12 — Container images and the Helm chart

**Model:** Sonnet · **Wave:** 4.1 · **Scope:** `Dockerfile*`, `chart/`, `.github/workflows/`

Sonnet because there is a working reference to mirror (`~/fa/av-tools-infra`) and the
shape is specified. Adaptation, not invention.

## Instructions given
> Multi-stage images for backend, frontend and docs. A Helm chart in the house style —
> `values.yaml`, `values-qa.yaml`, `values-prod.yaml`, `_helpers.tpl`, `NOTES.txt` — with
> probes wired to the Actuator endpoints that already exist. CI builds and pushes to
> ghcr.io. No application code changes.
