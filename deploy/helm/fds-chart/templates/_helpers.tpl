{{- define "fds.labels" -}}
app.kubernetes.io/name: {{ .Chart.Name }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ .Chart.AppVersion }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{- define "fds.selectorLabels" -}}
app.kubernetes.io/name: {{ .Chart.Name }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{- define "fds.syncFacade.image" -}}
{{- $reg := .Values.global.image.registry -}}
{{- if $reg }}{{ $reg }}/{{ end }}fds/sync-facade:{{ .Values.global.image.tag }}
{{- end }}

{{- define "fds.ruleCheckWorker.image" -}}
{{- $reg := .Values.global.image.registry -}}
{{- if $reg }}{{ $reg }}/{{ end }}fds/rule-check-worker:{{ .Values.global.image.tag }}
{{- end }}
