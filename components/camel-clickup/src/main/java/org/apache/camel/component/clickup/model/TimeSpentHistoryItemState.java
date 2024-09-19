package org.apache.camel.component.clickup.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.apache.camel.component.clickup.UnixTimestampDeserializer;
import org.apache.camel.component.clickup.UnixTimestampSerializer;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

public class TimeSpentHistoryItemState implements Serializable {

    @Serial
    private static final long serialVersionUID = 0L;

    @JsonProperty("id")
    private String id; // example: "4207094451598598611",

    @JsonProperty("start")
    @JsonDeserialize(using = UnixTimestampDeserializer.class)
    @JsonSerialize(using = UnixTimestampSerializer.class)
    private Instant start; // example: "1726529707994",

    @JsonProperty("end")
    @JsonDeserialize(using = UnixTimestampDeserializer.class)
    @JsonSerialize(using = UnixTimestampSerializer.class)
    private Instant end; // example: "1726558507994",

    @JsonProperty("time")
    private String time; // example: "28800000",

    @JsonProperty("source")
    private String source; // example: "clickup",

    @JsonProperty("date_added")
    @JsonDeserialize(using = UnixTimestampDeserializer.class)
    @JsonSerialize(using = UnixTimestampSerializer.class)
    private Instant dateAdded; // example: "1726558509952"

    public String getId() {
        return id;
    }

    public Instant getStart() {
        return start;
    }

    public Instant getEnd() {
        return end;
    }

    public String getTime() {
        return time;
    }

    public String getSource() {
        return source;
    }

    public Instant getDateAdded() {
        return dateAdded;
    }

    @Override
    public String toString() {
        return "TimeSpentHistoryItemState{" +
                "id='" + id + '\'' +
                ", start=" + start +
                ", end=" + end +
                ", time='" + time + '\'' +
                ", source='" + source + '\'' +
                ", dateAdded=" + dateAdded +
                '}';
    }

}
