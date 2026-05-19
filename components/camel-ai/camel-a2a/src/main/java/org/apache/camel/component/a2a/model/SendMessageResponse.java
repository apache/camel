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
package org.apache.camel.component.a2a.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A2A send message response.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SendMessageResponse {
    private Task task;
    private Message message;

    public SendMessageResponse() {
    }

    public Task getTask() {
        return task;
    }

    public void setTask(Task task) {
        if (task != null && message != null) {
            throw new IllegalArgumentException("SendMessageResponse must contain exactly one of task or message");
        }
        this.task = task;
    }

    public Message getMessage() {
        return message;
    }

    public void setMessage(Message message) {
        if (message != null && task != null) {
            throw new IllegalArgumentException("SendMessageResponse must contain exactly one of task or message");
        }
        this.message = message;
    }

    @JsonIgnore
    public void validate() {
        if ((task == null) == (message == null)) {
            throw new IllegalArgumentException("SendMessageResponse must contain exactly one of task or message");
        }
    }

    @JsonIgnore
    public boolean isTaskResponse() {
        return task != null;
    }

    @JsonIgnore
    public boolean isMessageResponse() {
        return message != null;
    }
}
