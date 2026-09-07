# syntax=docker/dockerfile:1
#
# Multi-stage build: mkdocs build -> static nginx.
#
# Build context is the REPO ROOT, not docs/ — mkdocs.yml and requirements.txt
# live there (see mkdocs.yml's `docs_dir` default: ./docs relative to itself).
# Build with:  docker build -f docs/Dockerfile -t gotthard-docs .
FROM python:3.13-slim AS build
WORKDIR /site

COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt

COPY mkdocs.yml ./
COPY docs ./docs
RUN mkdocs build --strict --site-dir /site/dist

# --- runtime ----------------------------------------------------------------
# nginx-unprivileged: already runs as non-root (uid 101) on port 8080 by
# default — matches the chart's runAsNonRoot pod securityContext for free.
FROM nginxinc/nginx-unprivileged:1.27-alpine AS runtime

COPY --from=build /site/dist /usr/share/nginx/html

EXPOSE 8080
