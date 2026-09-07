# syntax=docker/dockerfile:1
#
# Multi-stage build: npm build -> static nginx.
#
# `/api` is reverse-proxied to the backend Service so the browser sees one
# origin for both the SPA and the API — exactly what vite.config.ts's dev-time
# proxy does, and for the same reason: the session cookie is
# `SameSite=Strict` (see the backend's SecurityConfig), which only survives a
# same-origin request. The backend's actual Service DNS name isn't known at
# image build time (it depends on the Helm release name), so it's substituted
# into nginx/default.conf.template at container *start* via nginx's own
# envsubst-on-templates entrypoint script — see BACKEND_UPSTREAM below and
# chart/templates/deployment-frontend.yaml, which sets it per-release.
FROM node:22-alpine AS build
WORKDIR /app

# package.json + lockfile first so `npm ci` is its own cached Docker layer.
COPY package.json package-lock.json ./
RUN npm ci

COPY tsconfig.json tsconfig.app.json tsconfig.node.json vite.config.ts index.html ./
COPY public ./public
COPY src ./src
RUN npm run build

# --- runtime ----------------------------------------------------------------
# nginx-unprivileged: already runs as a non-root user (uid 101) on port 8080
# by default, so the chart's runAsNonRoot pod securityContext needs nothing
# extra done to this image.
FROM nginxinc/nginx-unprivileged:1.27-alpine AS runtime

COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template

# Rewritten to the release's real Service address by the chart; this default
# only matters for `docker run` outside Kubernetes.
ENV BACKEND_UPSTREAM=backend:8080
# Makes the base image export $NGINX_LOCAL_RESOLVERS from /etc/resolv.conf —
# see the `resolver` line in nginx/default.conf.template for why.
ENV NGINX_ENTRYPOINT_LOCAL_RESOLVERS=1

EXPOSE 8080
