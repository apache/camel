package org.apache.camel.component.clickup.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serial;
import java.io.Serializable;

public class TaskTimeTrackedUpdatedEventData implements Serializable {

    @Serial
    private static final long serialVersionUID = 0L;

    public static final String TIME_TRACKING_CREATED_DESCRIPTION = "Time Tracking Created";

    @JsonProperty("description")
    private String description; // example: "Time Tracking Created",

    @JsonProperty("interval_id")
    private String IntervalId; // example: "4207094451598598611"

    public String getDescription() {
        return description;
    }

    public String getIntervalId() {
        return IntervalId;
    }

    public boolean isCreation() {
        return this.description.equals(TIME_TRACKING_CREATED_DESCRIPTION);
    }

    @Override
    public String toString() {
        return "TaskTimeTrackedUpdatedEventData{" +
                "description='" + description + '\'' +
                ", IntervalId='" + IntervalId + '\'' +
                '}';
    }

}
