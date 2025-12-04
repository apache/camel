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
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TaskTimeTrackedUpdatedEvent extends Event implements Serializable {

    @Serial
    private static final long serialVersionUID = 0L;

    @JsonProperty("task_id")
    private String taskId;

    @JsonProperty("history_items")
    private List<TimeSpentHistoryItem> historyItems;

    @JsonProperty("data")
    private TaskTimeTrackedUpdatedEventData data;

    public String getTaskId() {
        return taskId;
    }

    public List<TimeSpentHistoryItem> getHistoryItems() {
        return historyItems;
    }

    public TaskTimeTrackedUpdatedEventData getData() {
        return data;
    }

    public TaskTimeTrackedUpdatedEventAction getAction() {
        return this.data.getEventAction();
    }

    @Override
    public String toString() {
        return "TaskTimeTrackedUpdatedEvent{" + "taskId='"
                + taskId + '\'' + ", historyItems="
                + historyItems + ", data="
                + data + ", webhookId='"
                + webhookId + '\'' + '}';
    }
}
