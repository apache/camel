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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A2A error response.
 */
public class A2AError {
    private String code;
    private String message;
    private List<Map<String, Object>> details;

    public A2AError() {
        this.details = new ArrayList<>();
    }

    public A2AError(String code, String message) {
        this();
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<Map<String, Object>> getDetails() {
        return details;
    }

    public void setDetails(List<Map<String, Object>> details) {
        this.details = details;
    }

    @SuppressWarnings("unchecked")
    @JsonProperty("error")
    public void setWrappedError(Map<String, Object> error) {
        if (error == null) {
            return;
        }
        Object messageValue = error.get("message");
        if (messageValue != null) {
            message = String.valueOf(messageValue);
        }
        Object detailsValue = error.get("details");
        if (detailsValue instanceof List<?> list) {
            details = (List<Map<String, Object>>) list;
            code = codeFromDetails(details);
        }
        if (code == null && error.get("status") != null) {
            code = String.valueOf(error.get("status"));
        }
        if (code == null && error.get("code") != null) {
            code = String.valueOf(error.get("code"));
        }
    }

    private static String codeFromDetails(List<Map<String, Object>> details) {
        if (details == null) {
            return null;
        }
        for (Map<String, Object> detail : details) {
            Object reason = detail.get("reason");
            if (reason instanceof String text && !text.isBlank()) {
                return errorCodeFromReason(text);
            }
        }
        return null;
    }

    private static String errorCodeFromReason(String reason) {
        StringBuilder answer = new StringBuilder();
        for (String part : reason.toLowerCase().split("_")) {
            if (!part.isEmpty()) {
                answer.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
        }
        return answer.append("Error").toString();
    }
}
