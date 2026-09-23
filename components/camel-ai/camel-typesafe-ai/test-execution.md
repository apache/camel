# TypeSafe AI test execution

Run these commands from `components/camel-ai/camel-typesafe-ai` after building the
Camel dependencies. `TypeSafeAiExamplesTest` starts real Camel routes for the
quickstart, triage, extraction cascade, and tool selection examples. It sends
their requests through the TypeSafe AI HTTP component.

## Local mock (default)

```bash
mvn test
```

The example routes use `camel-test-infra-typesafe-ai-mock`. It starts a local
System One HTTP server on a random loopback port. Its deterministic answers
verify route wiring and response handling, not model inference. No Docker or
model download is needed.

## External System One API

Set a base URL (without `/v1/systemone`) and bearer token to bypass the mock:

```bash
TYPESAFE_AI_BASE_URL=http://127.0.0.1:8000 \
TYPESAFE_AI_API_KEY=local-test \
TYPESAFE_AI_MODEL=laya-rl-agent \
mvn verify -Dit.test=TypeSafeAiExternalServiceIT
```

`TYPESAFE_AI_MODEL` is optional. The test service also accepts `LAYA_BASE_URL`,
`LAYA_API_KEY`, and `LAYA_MODEL` as fallback variable names. The external API
must implement `POST /v1/systemone` with the Noul, Choice, and Score response
shapes. `TypeSafeAiExternalServiceIT` is skipped when no external base URL is
configured. It runs the same four route examples as the mock test. The
assertions accept varying model decisions and verify that the Camel route uses
the returned values correctly.

### Local Laya example

From a Laya source checkout on macOS, start its HTTP server in a separate terminal:

```bash
uv venv .venv --python /usr/local/bin/python3
uv pip install --python .venv/bin/python -e '.[serve]'
LAYA_API_KEY=local-test LAYA_DEVICE=mps LAYA_PRELOAD=0 .venv/bin/python -m laya.serve
```

The server listens on port 8000 by default. Check it from the Laya checkout:

```bash
curl -f http://127.0.0.1:8000/v1/systemone \
    -H 'Authorization: Bearer local-test' \
    -H 'Content-Type: application/json' \
    --data-binary @examples/docker/request.json
```

Then run the external test command above from the Camel component directory.
The test does not start, stop, or modify the external service.
