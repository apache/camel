package org.apache.camel.component.clickup.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Set;

public class WebhooksReadResult {

    @JsonProperty("webhooks")
    private Set<Webhook> webhooks;

    public Set<Webhook> getWebhooks() {
        return webhooks;
    }

    @Override
    public String toString() {
        return "WebhooksReadResult{" +
                "webhooks=" + webhooks +
                '}';
    }

}
