# Referenz für Observability-Konfiguration

Zentraler Einstieg für die Demo-Konfigurationen rund um Tracing, Metriken und Logs.

## Konfigurationsindex

- OTel Collector (Tail Sampling, Export nach Tempo): `docker/otel-collector/otel-collector.yaml`
- Prometheus (Scrapes + OpenMetrics-Header): `docker/prometheus/prometheus.yml`
- Promtail (Log-Parsing + Labeling für Loki): `docker/promtail/promtail-config.yml`
- Tempo (Trace-Speicher + Metrikgenerator): `docker/tempo/tempo.yaml`
- Grafana Datasources (Tempo/Loki/Prometheus): `docker/grafana/provisioning/datasources/datasources.yaml`

## Copy/Paste-Checkliste für das eigene Projekt

1. **OTLP-Ingest festlegen**
   - App/SDK auf OTLP gRPC (4317) oder HTTP (4318) konfigurieren.
   - In diesem Demo-Stack sendet die App per OTLP/HTTP an `otel-collector:4318`; der Collector exportiert per OTLP/HTTP an Tempo (`tempo:4318`).
   - Collector-/Tempo-Endpunkte konsistent halten.

2. **Trace-Sampling-Regeln priorisieren**
   - `ERROR` immer behalten.
   - Langsame Traces über SLO-Schwelle behalten.
   - Business-Routen (z. B. Order-Flow) immer behalten.
   - Technische Endpunkte (`/q/*`, `/actuator/*`) gezielt herunter-samplen.

3. **Erforderliche Trace-Attribute sicherstellen**
   - Für routebasierte Policies muss `http.route` zuverlässig gesetzt sein.
   - Semantische Konventionen der Instrumentation prüfen.

4. **Metriken für Korrelation vorbereiten**
   - Quarkus mit OpenMetrics-Header scrapen.
   - Prometheus Exemplar-Storage aktivieren (z. B. per `--enable-feature=exemplar-storage`).
   - Instrumentation so konfigurieren, dass Exemplars (Trace-IDs) exportiert werden.

5. **Log-Korrelation aktivieren**
   - Logs in parsebarem Format (z. B. logfmt) ausgeben.
   - Felder wie `trace_id`, `span_id`, `order_id` mitschreiben.
   - Promtail-Pipeline auf das tatsächliche Logformat abstimmen.

6. **Retention und Storage planen**
   - Tempo `block_retention` passend zur Analysezeit setzen.
   - Bei lokalem Backend auf Disk-Budget und I/O achten.

## Hinweise zum lokalen Demo-Setup

- Der OTel Collector nutzt im Demo-Stack persistente Export-Queues über `file_storage`.
- Das Verzeichnis liegt unter `/tmp/otelcol/file_storage`, damit es im Container sicher beschreibbar ist.
- Wichtig: Diese Persistenz ist container-lokal. Bei Container-Neuerstellung können Queue-Daten verloren gehen.
- Für produktionsnahe Umgebungen sollte stattdessen ein dedizierter, als Volume gemounteter Pfad verwendet werden.
