# Running LRA Integration Tests Manually

The LRA integration tests require a Narayana LRA coordinator.
By default, the test infrastructure starts one via Docker (Testcontainers),
but on platforms where the Docker image is unavailable (e.g. aarch64 Mac)
you can run the coordinator natively and point the tests at it.

## 1. Extract the coordinator from the Docker image

```bash
mkdir -p /tmp/lra-coordinator && cd /tmp/lra-coordinator

# Pull the image and copy the Quarkus app out
docker create --name lra-tmp quay.io/jbosstm/lra-coordinator:5.13.1.Final-2.16.6.Final
docker cp lra-tmp:/deployments/. .
docker rm lra-tmp
```

## 2. Start the coordinator

The coordinator listens on port 8080 by default.
Use shortened recovery periods so failed participant callbacks are retried
quickly (the Docker container uses the same settings):

```bash
cd /tmp/lra-coordinator

java \
  -Dcom.arjuna.ats.arjuna.recovery.periodicRecoveryPeriod=2 \
  -Dcom.arjuna.ats.arjuna.recovery.recoveryBackoffPeriod=1 \
  -jar quarkus-run.jar
```

Verify it is running:

```bash
curl -s http://localhost:8080/lra-coordinator
# Should return [] (empty JSON array)
```

## 3. Run the tests

The key system property to bypass Docker is `microprofile-lra.instance.type=remote`
(note the **dash** between `microprofile` and `lra`, not a dot).

Run from the `components/camel-lra` directory:

```bash
cd components/camel-lra

# Run all IT tests against the native coordinator
mvn test \
  -Dtest="LRAManualIT,LRAFailuresIT" \
  -Dmicroprofile-lra.instance.type=remote \
  -Dmicroprofile.lra.host=localhost \
  -Dmicroprofile.lra.port=8080 \
  -Dmicroprofile.lra.service.address=http://localhost:8080
```

### System properties reference

| Property | Description | Default |
|---|---|---|
| `microprofile-lra.instance.type` | Set to `remote` to skip Docker and use an external coordinator | `local-microprofile-lra-container` (Docker) |
| `microprofile.lra.host` | Coordinator hostname | `localhost` |
| `microprofile.lra.port` | Coordinator port | `8080` |
| `microprofile.lra.service.address` | Full coordinator base URL | `http://<host>:<port>` |
| `microprofile.lra.callback.host` | Host the coordinator uses to call back to the test JVM | `localhost` |

### Property name gotcha

The instance-type property uses a **dash** (`microprofile-lra.instance.type`),
matching the service name in `SimpleTestServiceBuilder`.
The other properties use **dots** (`microprofile.lra.*`).
Using the wrong separator silently falls back to Docker mode.
