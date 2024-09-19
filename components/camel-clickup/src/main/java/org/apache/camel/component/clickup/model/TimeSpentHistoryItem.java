package org.apache.camel.component.clickup.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serial;
import java.io.Serializable;

public class TimeSpentHistoryItem extends HistoryItem implements Serializable {

    @Serial
    private static final long serialVersionUID = 0L;

    @JsonProperty("id")
    private Long id;

    @JsonProperty("before")
    private TimeSpentHistoryItemState before;

    @JsonProperty("after")
    private TimeSpentHistoryItemState after;

    public Long getId() {
        return id;
    }

    public TimeSpentHistoryItemState getBefore() {
        return before;
    }

    public TimeSpentHistoryItemState getAfter() {
        return after;
    }

    @Override
    public String toString() {
        return "TimeSpentHistoryItem{" +
                "id=" + id +
                ", before=" + before +
                ", after=" + after +
                ", date=" + date +
                ", field='" + field + '\'' +
                ", user=" + user +
                '}';
    }

}
