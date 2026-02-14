# CoffeeShop-Runbook fuer Observability (SLO)

<a id="technical-http-error-rate"></a>
## Technische HTTP-Fehlerrate
- **Alert:** `CoffeeShopHighHttpErrorRate`
- **Bedeutung:** Die 5xx-Quote liegt mindestens 10 Minuten über 5 %.
- **Prüfungen:**
  - Dashboard: `CoffeeShop - Technical Overview`
  - Endpunkte mit den meisten Fehlern nach `status` und `uri` prüfen.
  - Loki-Logs nach `ERROR` mit passender `traceId` prüfen.
- **Maßnahmen:**
  - Letzte Änderung zurückrollen, falls zeitlich korreliert.
  - MariaDB- und Kafka-Konnektivität prüfen.
  - App-Replikate skalieren oder eingehende Last reduzieren.

<a id="technical-http-latency-p95"></a>
## Technische HTTP-Latenz p95
- **Alert:** `CoffeeShopHighHttpLatencyP95`
- **Bedeutung:** Die p95-Latenz liegt mindestens 10 Minuten über 1 Sekunde.
- **Prüfungen:**
  - HTTP-p95 mit Kitchen-p95 und DB-Spans vergleichen.
  - Tempo auf langsame Spans und dominante Operationen prüfen.
- **Maßnahmen:**
  - Engpass identifizieren (App, DB, Kafka, Kitchen-Worker-Verzögerung).
  - Künstliche Kitchen-Delay-Grenzen zur Incident-Minderung reduzieren.

<a id="technical-kitchen-backpressure"></a>
## Technische Kitchen-Backpressure
- **Alert:** `CoffeeShopKitchenBackpressureHigh`
- **Bedeutung:** Die Queue enthält mindestens 10 Minuten mehr als 12 wartende Orders.
- **Prüfungen:**
  - Backpressure-Gauge und ETA prüfen.
  - Submitted- gegen Ready-Rate vergleichen.
- **Maßnahmen:**
  - Anzahl der Kitchen-Consumer/Worker erhöhen.
  - Neue Orders vorübergehend begrenzen oder aufwendige Produkte deaktivieren.

<a id="technical-kitchen-failures"></a>
## Technische Kitchen-Fehler
- **Alert:** `CoffeeShopKitchenFailuresDetected`
- **Bedeutung:** In den letzten 10 Minuten sind Kitchen-Verarbeitungsfehler aufgetreten.
- **Prüfungen:**
  - Loki: nach `kueche` + `ERROR` suchen.
  - Tempo: fehlgeschlagene Traces im Alarmzeitraum prüfen.
- **Maßnahmen:**
  - Defekten Consumer bei Hänger neu starten.
  - Downstream-Abhängigkeiten und Message-Payload-Fehler prüfen.

<a id="business-customer-wait-p95"></a>
## Fachlich: Customer Wait p95
- **Alert:** `CoffeeShopCustomerWaitP95TooHigh`
- **Bedeutung:** Customer-Wait-p95 liegt mindestens 10 Minuten über 120 Sekunden.
- **Prüfungen:**
  - Mit Queue-Größe und Order-Flow-Imbalance korrelieren.
  - Drink-Mix prüfen, ob ein Produkt die Zubereitungszeit dominiert.
- **Maßnahmen:**
  - Personal-/Kitchen-Worker-Durchsatz neu ausbalancieren.
  - Drinks mit kurzer Zubereitungszeit aktiv promoten, um Queue-Druck zu senken.

<a id="business-kitchen-eta"></a>
## Fachlich: Kitchen ETA
- **Alert:** `CoffeeShopKitchenEtaTooHigh`
- **Bedeutung:** Die geschätzte Queue-Abbauzeit liegt mindestens 10 Minuten über 180 Sekunden.
- **Prüfungen:**
  - ETA gegen reale Wait-Messungen validieren.
  - Sicherstellen, dass kein Consumer-Lag oder keine Verarbeitungsfehler vorliegen.
- **Maßnahmen:**
  - Temporäre Kapazität hinzufügen, Menükomplexität reduzieren.
  - Burst-Handling oder Pacing bei der Order-Annahme einführen.

<a id="business-order-flow-imbalance"></a>
## Fachlich: Order-Flow-Imbalance
- **Alert:** `CoffeeShopOrderFlowImbalance`
- **Bedeutung:** Die eingehende Order-Rate liegt nachhaltig über der Ready-Rate.
- **Prüfungen:**
  - Submitted-/Ready-/Picked-up-Raten vergleichen.
  - Kitchen-Processing-p95 und Failure-Count validieren.
- **Maßnahmen:**
  - Verarbeitungskapazität erhöhen.
  - Queue-Schutzstrategie aktivieren.
