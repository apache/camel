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

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A2A send-message runtime configuration.
 *
 * @since 4.21
 */
@JsonIgnoreProperties(ignoreUnknown = false)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SendMessageConfiguration {
    private Boolean returnImmediately;
    private Boolean blocking;
    private Integer historyLength;
    private final Map<String, Object> additionalProperties = new LinkedHashMap<>();

    public Boolean getReturnImmediately() {
        return returnImmediately;
    }

    public void setReturnImmediately(Boolean returnImmediately) {
        this.returnImmediately = returnImmediately;
    }

    public Boolean getBlocking() {
        return blocking;
    }

    public void setBlocking(Boolean blocking) {
        this.blocking = blocking;
    }

    public Integer getHistoryLength() {
        return historyLength;
    }

    public void setHistoryLength(Integer historyLength) {
        this.historyLength = historyLength;
    }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) {
        additionalProperties.put(name, value);
    }

    public static SendMessageConfiguration from(Map<String, Object> values) {
        SendMessageConfiguration answer = new SendMessageConfiguration();
        if (values == null) {
            return answer;
        }
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            switch (entry.getKey()) {
                case "returnImmediately" -> answer.setReturnImmediately(toBoolean(entry.getValue()));
                case "blocking" -> answer.setBlocking(toBoolean(entry.getValue()));
                case "historyLength" -> answer.setHistoryLength(toInteger(entry.getValue()));
                default -> answer.setAdditionalProperty(entry.getKey(), entry.getValue());
            }
        }
        return answer;
    }

    private static Boolean toBoolean(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof String stringValue) {
            return Boolean.parseBoolean(stringValue);
        }
        return Boolean.valueOf(String.valueOf(value));
    }

    private static Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number numberValue) {
            return numberValue.intValue();
        }
        if (value instanceof String stringValue) {
            return Integer.valueOf(stringValue);
        }
        return Integer.valueOf(String.valueOf(value));
    }
}
