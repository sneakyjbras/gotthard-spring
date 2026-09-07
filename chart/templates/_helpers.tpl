{{/* Chart name (overridable). */}}
{{- define "gotthard.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
Fully-qualified base name. One release per namespace (qa/prod are separate
namespaces, not separate releases in the same one — see values-qa.yaml /
values-prod.yaml), so this deliberately does NOT fold in Release.Name, the
same choice ~/fa/av-tools-infra's avtools.fullname makes and for the same
reason: it would add noise to every resource name without preventing any
real collision in this deployment model.
*/}}
{{- define "gotthard.fullname" -}}
{{- if .Values.fullnameOverride -}}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- $name := default .Chart.Name .Values.nameOverride -}}
{{- printf "%s" $name | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}

{{/* Per-component resource name, e.g. "gotthard-backend". */}}
{{- define "gotthard.componentName" -}}
{{- printf "%s-%s" (include "gotthard.fullname" .root) .component | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
Component-scoped selector labels. Call with a dict: (dict "root" $ "component" "backend").
Kept separate from the full label set below because Service/Deployment
selectors must never gain a label that later changes (helm.sh/chart's version
segment, for one) — Kubernetes selectors are immutable on Deployment/Service.
*/}}
{{- define "gotthard.selectorLabels" -}}
app.kubernetes.io/name: {{ include "gotthard.name" .root }}
app.kubernetes.io/instance: {{ .root.Release.Name }}
app.kubernetes.io/component: {{ .component }}
{{- end -}}

{{/* Full label set for a component. Same dict form as selectorLabels. */}}
{{- define "gotthard.labels" -}}
{{ include "gotthard.selectorLabels" . }}
app.kubernetes.io/managed-by: {{ .root.Release.Service }}
app.kubernetes.io/part-of: gotthard
helm.sh/chart: {{ printf "%s-%s" .root.Chart.Name .root.Chart.Version | replace "+" "_" }}
gotthard/environment: {{ .root.Values.environment }}
{{- end -}}

{{/* Service account name shared by every component (none need distinct RBAC). */}}
{{- define "gotthard.serviceAccountName" -}}
{{- default (include "gotthard.fullname" .) .Values.serviceAccount.name -}}
{{- end -}}
