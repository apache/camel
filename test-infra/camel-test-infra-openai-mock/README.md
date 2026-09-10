# OpenAI Mock

OpenAI Mock is a lightweight Java library for mocking OpenAI's chat completions API (`/v1/chat/completions`) within your unit tests. It allows you to simulate responses from an LLM accessed through OpenAI API without making actual network calls, making your tests faster, more reliable, and independent of external services.

It uses the native Java `HttpServer` to run a local web server that intercepts requests to the OpenAI API and returns predefined responses.

## Usage

Here's an example of how to use `OpenAIMock` in your JUnit 5 tests:

```java
import com.example.OpenAIMock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
// ... other imports

public class MyOpenAIApiTest {

    @RegisterExtension
    public OpenAIMock openAIMock = new OpenAIMock().builder()
            .when("any sentence")
                .invokeTool("toolName")
                .withParam("param1", "value1")
            .end()
            .when("another sentence")
                .replyWith("hello World")
            .end()
            .when("multiple sequential tools")
                .invokeTool("tool3")
                .withParam("p3", "v3")
                .withParam("p4", "v5")
                .andThenInvokeTool("tool 4")
                .withParam("p4", "v4")
            .when("multiple tools")
                .invokeTool("tool1")
                .withParam("p1", "v1")
                .andInvokeTool("tool2")
                .withParam("p2", "v2")
            .end()
            .when("custom response")
                .thenRespondWith((exchange, input) -> {
                    try {
                        String responseBody = "Custom response for: " + input;
                        byte[] responseBytes = responseBody.getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().set("Content-Type", "text/plain");
                        exchange.sendResponseHeaders(200, responseBytes.length);
                        try (OutputStream os = exchange.getResponseBody()) {
                            os.write(responseBytes);
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    return null;
                })
            .build();

    @Test
    public void testMyApi() throws Exception {
        // Your code that calls the OpenAI API
        // Make sure your HTTP client is configured to use openAIMock.getBaseUrl()
        // For example:
        String baseUrl = openAIMock.getBaseUrl();
        // ... rest of your test code
    }
}
```

## Asserting on received requests

The mock records every request it receives. Assert on them after the call, on the test thread, instead of inside
an `assertRequest` callback, where a failed assertion surfaces as an HTTP error for the client under test:

```java
RecordedRequest request = openAIMock.getLastRequest();
assertEquals("/v1/responses", request.path());
assertEquals("gpt-5", request.bodyAsJson().path("model").asText());
```

`getReceivedRequests()` returns all of them in arrival order. The list is cleared when the mock server starts.

## Responses API

`when(...)` expectations also answer `POST /v1/responses`, matched on the last input text. `replyWith` returns a
single text message, `thenRespondWith` a custom body, and `replyWithResponsesOutput` any output items, for example an
MCP approval request or a message whose text carries citations:

```java
.when("What is Apache Camel?")
    .replyWithResponsesOutput("""
        [{"type": "mcp_approval_request", "id": "mcpr_1", "server_label": "deepwiki",
          "name": "ask_question", "arguments": "{}"}]""")
.end()
```

`invokeTool` sequences work for the Responses API too. The mock replies with `function_call` items, and a
follow-up request whose input ends with `function_call_output` items gets the next step of the sequence, then the
`replyWith` text.