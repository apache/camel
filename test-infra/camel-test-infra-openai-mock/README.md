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