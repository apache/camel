# TypeSafe AI test service

`TypeSafeAiService` is a JUnit extension for tests using the System One API.
It starts a local HTTP mock on a random loopback port by default. The mock
returns contract-compatible, deterministic answers for `noul`, `choice`, and
`score` questions. These answers test route plumbing and response handling;
they do not test inference quality.

```java
@RegisterExtension
TypeSafeAiService service = new TypeSafeAiService();

// Configure the component before the test route starts:
component.getConfiguration().setBaseUrl(service.getBaseUrl());
component.getConfiguration().setApiKey(service.getApiKey());
component.getConfiguration().setModel(service.getModel());
```

To bypass the mock and run the same test against a compatible API such as a
local Laya server, set `TYPESAFE_AI_BASE_URL` and `TYPESAFE_AI_API_KEY`.
`TYPESAFE_AI_MODEL` is optional. `LAYA_BASE_URL`, `LAYA_API_KEY`, and
`LAYA_MODEL` are accepted as fallbacks. From
`components/camel-ai/camel-typesafe-ai`, for example:

```sh
TYPESAFE_AI_BASE_URL=http://127.0.0.1:8000 \
TYPESAFE_AI_API_KEY=local-test \
mvn test -Dtest=TypeSafeAiExamplesTest
```

The remote mode sends requests directly to the configured service. The mock
does not proxy or record them. Tests that need a particular answer can call
`setResponder` with a function that returns answers keyed by question name.
