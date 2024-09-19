package org.apache.camel.component.clickup.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.apache.camel.component.clickup.UnixTimestampDeserializer;
import org.apache.camel.component.clickup.UnixTimestampSerializer;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class HistoryItem {

    @JsonProperty("date")
    @JsonDeserialize(using = UnixTimestampDeserializer.class)
    @JsonSerialize(using = UnixTimestampSerializer.class)
    protected Instant date;

    @JsonProperty("field")
    protected String field;

    @JsonProperty("user")
    protected User user;

    public Instant getDate() {
        return date;
    }

    public String getField() {
        return field;
    }

    public User getUser() {
        return user;
    }

    @Override
    public String toString() {
        return "HistoryItem{" +
                "date=" + date +
                ", field='" + field + '\'' +
                ", user=" + user +
                '}';
    }

}
