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
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;

/**
 * An event of a kind that has no class of its own, such as taskCreated: the fields other than event and webhook_id are
 * kept as they are in the JSON.
 */
public class GenericEvent extends Event implements Serializable {

    @Serial
    private static final long serialVersionUID = 0L;

    private final Map<String, Object> properties = new LinkedHashMap<>();

    /**
     * The fields of the event other than event and webhook_id, such as task_id and history_items.
     */
    @JsonAnyGetter
    public Map<String, Object> getProperties() {
        return properties;
    }

    @JsonAnySetter
    public void setProperty(String name, Object value) {
        properties.put(name, value);
    }

    @Override
    public String toString() {
        return "GenericEvent{" +
               "event='" + event + '\'' +
               ", webhookId='" + webhookId + '\'' +
               ", properties=" + properties +
               '}';
    }

}
