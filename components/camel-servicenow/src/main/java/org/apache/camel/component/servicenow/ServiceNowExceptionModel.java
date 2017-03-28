package org.apache.camel.component.servicenow;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class ServiceNowExceptionModel {
    private final String status;
    private final Map<String, String> error;

    public ServiceNowExceptionModel(
        @JsonProperty("status") String status,
        @JsonProperty("error") Map<String, String> error) {
        this.status = status;
        this.error = error;
    }

    public String getStatus() {
        return status;
    }

    public Map<String, String> getError() {
        return error;
    }
}
