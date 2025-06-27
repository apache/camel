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
package org.apache.camel.opentelemetry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.opentelemetry.api.trace.SpanKind;

public class SpanTestData {

    private String label;
    private String uri;
    private String operation;
    private SpanKind kind = SpanKind.INTERNAL;
    private String traceId;
    private int parentId = -1;
    private final List<String> logMessages = new ArrayList<>();
    private final Map<String, String> tags = new HashMap<>();
    private final ArrayList<SpanTestData> children = new ArrayList<>();
    private final Map<String, String> baggage = new HashMap<>();

    public String getLabel() {
        return label;
    }

    public SpanTestData setLabel(String label) {
        this.label = label;
        return this;
    }

    public String getUri() {
        return uri;
    }

    public SpanTestData setUri(String uri) {
        this.uri = uri;
        return this;
    }

    public String getOperation() {
        return operation;
    }

    public SpanTestData setOperation(String operation) {
        this.operation = operation;
        return this;
    }

    public SpanKind getKind() {
        return kind;
    }

    public SpanTestData setKind(SpanKind kind) {
        this.kind = kind;
        return this;
    }

    public String getTraceId() {
        return traceId;
    }

    public SpanTestData setTraceId(String traceId) {
        this.traceId = traceId;
        return this;
    }

    public int getParentId() {
        return parentId;
    }

    public SpanTestData setParentId(int parentId) {
        this.parentId = parentId;
        return this;
    }

    public SpanTestData addLogMessage(String logMessage) {
        logMessages.add(logMessage);
        return this;
    }

    public List<String> getLogMessages() {
        return logMessages;
    }

    public SpanTestData addTag(String key, String val) {
        tags.put(key, val);
        return this;
    }

    public Map<String, String> getTags() {
        return tags;
    }

    public SpanTestData setChildren(SpanTestData[] children) {
        Collections.addAll(this.children, children);
        return this;
    }

}
