package org.apache.camel.component.clickup;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@UriParams
public class ClickUpConfiguration {

    @UriPath(description = "The WorkspaceID.")
    @Metadata(required = true)
    private Long workspaceId;

    @UriParam(description = "Can be used to set an alternative base URL, e.g. when you want to test the component against a mock ClickUp API", label = "advanced")
    private String baseUrl;

    @UriParam(description = "The authorization token for authenticating against the ClickUp API.", label = "security", secret = true)
    private String authorizationToken;

    @UriParam(description = "The shared secret obtained in the webhook creation response.", label = "security", secret = true)
    private String webhookSecret;

    @UriParam(description = "The comma separated list of events to which the webhook must subscribe")
    @Metadata(required = true)
    private String events;
    
    public Long getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(Long workspaceId) {
        this.workspaceId = workspaceId;
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    public String getAuthorizationToken() {
        return authorizationToken;
    }

    public void setAuthorizationToken(String authorizationToken) {
        this.authorizationToken = authorizationToken;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Set<String> getEvents() {
        return Arrays.stream(events.split(","))
                .collect(Collectors.toSet());
    }

    public void setEvents(String events) {
        this.events = events;
    }

    @Override
    public String toString() {
        return "ClickUpConfiguration{" +
                "workspaceId=" + workspaceId +
                ", webhookSecret='" + webhookSecret + '\'' +
                ", authorizationToken='" + authorizationToken + '\'' +
                ", baseUrl='" + baseUrl + '\'' +
                '}';
    }

}
