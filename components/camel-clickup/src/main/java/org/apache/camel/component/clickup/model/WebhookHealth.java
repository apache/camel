package org.apache.camel.component.clickup.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class WebhookHealth {
    @JsonProperty("status")
    private String status = null;

    @JsonProperty("fail_count")
    private Integer failCount = null;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getFailCount() {
        return failCount;
    }

    public void setFailCount(Integer failCount) {
        this.failCount = failCount;
    }

    @Override
    public String toString() {
        return "WebhookHealth{" +
                "status='" + status + '\'' +
                ", failCount=" + failCount +
                '}';
    }
}
