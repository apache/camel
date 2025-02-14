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
package org.apache.camel.telemetry.mock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.camel.telemetry.Span;
import org.apache.camel.telemetry.TagConstants;

public class MockSpanAdapter implements Span {

    private final List<LogEntry> logEntries = new ArrayList<>();
    private final Map<String, String> tags = new HashMap<>();

    public static long nowMicros() {
        return System.currentTimeMillis() * 1000;
    }

    public static MockSpanAdapter buildSpan(String operation) {
        return new MockSpanAdapter();
    }

    public Map<String, String> tags() {
        return tags;
    }

    @Override
    public void setComponent(String component) {
        this.tags.put(TagConstants.COMPONENT, component);
    }

    @Override
    public void setError(boolean error) {
        this.tags.put(TagConstants.ERROR, "" + error);
    }

    @Override
    public void setTag(String key, String value) {
        this.tags.put(key, value);
    }

    public String getTag(String key) {
        return this.tags.get(key);
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

        @Override
        public String toString() {
            return timestampMicros + fields.toString();
        }
    }

    @Override
    public String toString() {
        String toString = this.tags.get("traceid") + "-" + this.tags.get("spanid") + "-[";
        for (Entry<String, String> entry : this.tags().entrySet()) {
            if (!entry.getKey().equals("traceid") && !entry.getKey().equals("spanid")) {
                toString += entry.getKey() + "=" + entry.getValue() + ",";
            }
        }
        toString += "]";
        return toString;
    }

}
