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
package org.apache.camel.component.salesforce.api.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class PlatformEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private final ZonedDateTime created;

    private final String createdById;

    private final Map<String, String> eventData = new HashMap<>();

    @JsonCreator
    public PlatformEvent(@JsonProperty("CreatedDate")
    final ZonedDateTime created, @JsonProperty("CreatedById")
    final String createdById) {
        this.created = created;
        this.createdById = createdById;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof PlatformEvent)) {
            return false;
        }

        final PlatformEvent other = (PlatformEvent)obj;

        return Objects.equals(created, other.created) && Objects.equals(createdById, other.createdById) && Objects.equals(eventData, other.eventData);
    }

    public ZonedDateTime getCreated() {
        return created;
    }

    public String getCreatedById() {
        return createdById;
    }

    public Map<String, String> getEventData() {
        return eventData;
    }

    @Override
    public int hashCode() {
        return Objects.hash(created, createdById, eventData);
    }

    @JsonAnySetter
    public void set(final String name, final String value) {
        eventData.put(name, value);
    }

    @Override
    public String toString() {
        return new StringBuilder().append("PlatformEvent: createdById: ").append(createdById).append(", createdId: ").append(created).append(", data: ").append(eventData)
            .toString();
    }
}
