/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.camel.component.clickup.model;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.apache.camel.component.clickup.UnixTimestampDeserializer;
import org.apache.camel.component.clickup.UnixTimestampSerializer;

@JsonIgnoreProperties(ignoreUnknown = true)
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
        return "TimeSpentHistoryItemState{" + "id='"
                + id + '\'' + ", start="
                + start + ", end="
                + end + ", time='"
                + time + '\'' + ", source='"
                + source + '\'' + ", dateAdded="
                + dateAdded + '}';
    }
}
