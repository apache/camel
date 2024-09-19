package org.apache.camel.component.clickup.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "event", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = TaskTimeTrackedUpdatedEvent.class, name = Events.TASK_TIME_TRACKED_UPDATED)
})
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class Event {

    @JsonProperty("webhook_id")
    protected String webhookId;

    public String getWebhookId() {
        return webhookId;
    }

    @Override
    public String toString() {
        return "Event{" +
                "webhookId='" + webhookId + '\'' +
                '}';
    }

}
