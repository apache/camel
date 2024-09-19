package org.apache.camel.component.clickup.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class WebhookCreationResult {

    @JsonProperty("id")
    private String id;

    @JsonProperty("webhook")
    private Webhook webhook;

    @JsonProperty("err")
    private String error;

    @JsonProperty("ECODE")
    private String errorCode;

    public String getId() {
        return id;
    }

    public Webhook getWebhook() {
        return webhook;
    }

    public String getError() {
        return error;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public boolean isError() {
        return this.error != null;
    }

    @Override
    public String toString() {
        return "WebhookCreationResult{" +
                "id='" + id + '\'' +
                ", webhook=" + webhook +
                ", error='" + error + '\'' +
                ", errorCode='" + errorCode + '\'' +
                '}';
    }
}
