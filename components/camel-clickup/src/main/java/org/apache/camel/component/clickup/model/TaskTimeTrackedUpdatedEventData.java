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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TaskTimeTrackedUpdatedEventData implements Serializable {

    @Serial
    private static final long serialVersionUID = 0L;

    public static final String TIME_TRACKING_CREATED_DESCRIPTION = "Time Tracking Created";
    public static final String TIME_TRACKING_EDITED_DESCRIPTION = "Time Tracking Edited";
    public static final String TIME_TRACKING_DELETED_DESCRIPTION = "Time Tracking Deleted";

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

    public TaskTimeTrackedUpdatedEventAction getEventAction() {
        return switch (this.description) {
            case TIME_TRACKING_CREATED_DESCRIPTION -> TaskTimeTrackedUpdatedEventAction.CREATION;
            case TIME_TRACKING_EDITED_DESCRIPTION -> TaskTimeTrackedUpdatedEventAction.UPDATE;
            case TIME_TRACKING_DELETED_DESCRIPTION -> TaskTimeTrackedUpdatedEventAction.DELETION;
            default -> throw new RuntimeException(
                    "Could not determine event action. Unknown event description: " + this.description);
        };
    }

    @Override
    public String toString() {
        return "TaskTimeTrackedUpdatedEventData{" + "description='"
                + description + '\'' + ", IntervalId='"
                + IntervalId + '\'' + '}';
    }
}
