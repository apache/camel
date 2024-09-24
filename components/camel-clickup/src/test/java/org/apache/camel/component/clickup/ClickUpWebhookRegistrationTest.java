package org.apache.camel.component.clickup;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.clickup.model.WebhookCreationResult;
import org.apache.camel.component.clickup.util.ClickUpMockRoutes;
import org.apache.camel.component.clickup.util.ClickUpTestSupport;
import org.apache.camel.impl.DefaultCamelContext;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class ClickUpWebhookRegistrationTest extends ClickUpTestSupport {

    private final static Logger LOGGER = LoggerFactory.getLogger(ClickUpWebhookRegistrationTest.class);

    private final static Long WORKSPACE_ID = 12345L;
    private final static String AUTHORIZATION_TOKEN = "mock-authorization-token";
    private final static String WEBHOOK_SECRET = "mock-webhook-secret";
    private final static Set<String> EVENTS = new HashSet<>(List.of("taskTimeTrackedUpdated"));

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    public void testAutomaticRegistration() throws Exception {
        final ClickUpMockRoutes.MockProcessor<String> mockProcessor = getMockRoutes().getMock("team/" + WORKSPACE_ID + "/webhook");

        mockProcessor.clearRecordedMessages();

        try (final DefaultCamelContext mockContext = new DefaultCamelContext()) {
            mockContext.addRoutes(getMockRoutes());
            mockContext.start();

            /* Make sure the ClickUp mock API is up and running */
            Awaitility.await()
                    .atMost(25, TimeUnit.SECONDS)
                    .until(() -> {
                        HttpClient client = HttpClient.newBuilder().build();
                        HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create("http://localhost:" + port + "/clickup-api-mock/health")).GET().build();

                        final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                        return response.statusCode() == 200;
                    });

            context().addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    String apiMockBaseUrl = "http://localhost:" + port + "/clickup-api-mock";

                    from("webhook:clickup:" + WORKSPACE_ID + "?authorizationToken=" + AUTHORIZATION_TOKEN + "&webhookSecret="
                            + WEBHOOK_SECRET + "&events=" + String.join(",", EVENTS) + "&webhookAutoRegister=true&baseUrl=" + apiMockBaseUrl)
                            .id("webhook")
                            .to("mock:endpoint");
                }
            });

            context().start();

            {
                final List<String> recordedMessages = mockProcessor.awaitRecordedMessages(1, 5000);
                assertEquals(1, recordedMessages.size());
                String recordedMessage = recordedMessages.get(0);

                // TODO: assert recorded message format

                final List<String> responseBodies = mockProcessor.awaitResponseBodies(1, 5000);
                assertEquals(1, responseBodies.size());
                String responseBody = responseBodies.get(0);

                try {
                    // TODO: does it really make sense to test the api mock response?
                    WebhookCreationResult webhookCreationResult = MAPPER.readValue(responseBody, WebhookCreationResult.class);

                    assertInstanceOf(WebhookCreationResult.class, webhookCreationResult);
                } catch (IOException e) {
                    fail(e);
                }
            }

            mockProcessor.clearRecordedMessages();

            context().stop();

        }
    }

    @Override
    protected ClickUpMockRoutes createMockRoutes() {
        try (InputStream content
                     = getClass().getClassLoader().getResourceAsStream("messages/webhook-created.json")) {
            assert content != null;

            String webhookCreatedResponse = new String(content.readAllBytes());

            return new ClickUpMockRoutes(port)
                    .addEndpoint(
                            "team/" + WORKSPACE_ID + "/webhook",
                            "POST",
                            String.class,
                            webhookCreatedResponse
                    )
                    .addEndpoint(
                            "health",
                            "GET",
                            String.class,
                            ""
                    );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }
}
