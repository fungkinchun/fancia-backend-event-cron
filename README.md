# event-cron

Spring Boot batch Lambda that materialises upcoming `event_occurrences` rows for recurring events.

## Behaviour

- On startup, `EnsureUpcomingOccurrencesJob` loads all recurring events and calls `EventOccurrenceService.ensureUpcomingOccurrences`.
- Horizon: **8 weeks** ahead, max **52** new rows per event per run.
- Idempotent on `(event_id, start_time)`; copies HOST/COHOST participants from the first occurrence.
- Process exits after the job finishes (suitable for EventBridge → Lambda).
- Flyway, security, Kafka, Feign, and mail auto-config are disabled — schema ownership stays with **event-service**.
- JPA entities (`Event`, `EventOccurrence`, …) and `RecurringEventVisibility` live in **shared-event** (shared with event-service).

## Build

Requires AWS CodeArtifact credentials (same shared libs as event-service):

```bash
cp gradle.properties.example gradle.properties
# set ARTIFACT_REPO_PASSWORD from CodeArtifact, or export ARTIFACT_REPO_* env vars

./gradlew test
./gradlew lambdaZip   # → build/distributions/event-cron-lambda.zip
```

`buildspec.yml` refreshes the token in CodeBuild and deploys the zip (or ECR when `USE_EKS=true`).

## Local run

```bash
# .env with DATABASE_URL / DATABASE_USERNAME / DATABASE_PASSWORD
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

## Packaging

- Lambda: `src/lambda/run.sh` → `java -jar app.jar`
- Container: `Dockerfile` (Corretto 24)
