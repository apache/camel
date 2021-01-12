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
package org.apache.camel.tracing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MockSpanAdapter implements SpanAdapter {

    private List<LogEntry> logEntries = new ArrayList<>();
    private HashMap<String, Object> tags = new HashMap<>();

    static long nowMicros() {
        return System.currentTimeMillis() * 1000;
    }

    static MockSpanAdapter buildSpan(String operation) {
        return new MockSpanAdapter().setOperation(operation);
    }

    public HashMap<String, Object> tags() {
        return tags;
    }

    @Override
    public void setComponent(String component) {
        this.tags.put(Tag.COMPONENT.name(), component);
    }

    @Override
    public void setError(boolean error) {
        this.tags.put(Tag.ERROR.name(), error);
    }

    @Override
    public void setTag(Tag key, String value) {
        this.tags.put(key.name(), value);
    }

    @Override
    public void setTag(Tag key, Number value) {
        this.tags.put(key.name(), value);
    }

    @Override
    public void setTag(String key, String value) {
        this.tags.put(key, value);
    }

    @Override
    public void setTag(String key, Number value) {
        this.tags.put(key, value);
    }

    @Override
    public void setTag(String key, Boolean value) {
        this.tags.put(key, value);
    }

    @Override
    public void log(Map<String, String> fields) {
        this.logEntries.add(new LogEntry(nowMicros(), fields));
    }

    public List<LogEntry> logEntries() {
        return new ArrayList<>(this.logEntries);
    }

    public static final class LogEntry {
        private final long timestampMicros;
        private final Map<String, ?> fields;

        public LogEntry(long timestampMicros, Map<String, ?> fields) {
            this.timestampMicros = timestampMicros;
            this.fields = fields;
        }

        public long timestampMicros() {
            return timestampMicros;
        }

        public Map<String, ?> fields() {
            return fields;
        }
    }

    public MockSpanAdapter setOperation(String operation) {
        return this;
    }
}
