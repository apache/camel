package org.apache.camel.component.clickup;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.clickup.util.ClickUpTestSupport;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ClickUpConfigurationTest extends ClickUpTestSupport {

    private final static Long WORKSPACE_ID = 12345L;
    private final static String BASE_URL = "https://mock-api.clickup.com";
    private final static String AUTHORIZATION_TOKEN = "mock-authorization-token";
    private final static String WEBHOOK_SECRET = "mock-webhook-secret";
    private final static Set<String> EVENTS = new HashSet<>(Arrays.asList("taskTimeEstimateUpdated", "taskTimeTrackedUpdated"));

    @Test
    public void testChatBotResult() {
        ClickUpEndpoint endpoint = (ClickUpEndpoint) context().getEndpoints().stream()
                .filter(e -> e instanceof ClickUpEndpoint).findAny().get();
        ClickUpConfiguration config = endpoint.getConfiguration();

        assertEquals(WORKSPACE_ID, config.getWorkspaceId());
        assertEquals(BASE_URL, config.getBaseUrl());
        assertEquals(AUTHORIZATION_TOKEN, config.getAuthorizationToken());
        assertEquals(WEBHOOK_SECRET, config.getWebhookSecret());
        assertEquals(EVENTS, config.getEvents());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("webhook:clickup:" + WORKSPACE_ID + "?baseUrl=" + BASE_URL + "&authorizationToken=" + AUTHORIZATION_TOKEN + "&webhookSecret=" + WEBHOOK_SECRET + "&events=" + EVENTS)
                        .to("Received: ${body}");
            }
        };
    }

}
