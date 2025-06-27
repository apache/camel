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
package org.apache.camel.telemetrydev;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/*
 * Basic inmemory implementation for an home made spans collector.
 */
public class InMemoryCollector {

    // traceid --> spanid --> Span
    private Map<String, Map<String, DevSpanAdapter>> traceDB = new HashMap<>();

    public synchronized void push(String traceId, DevSpanAdapter span) {
        Map<String, DevSpanAdapter> spans = traceDB.get(traceId);
        if (spans == null) {
            spans = new HashMap<>();
            traceDB.put(traceId, spans);
        }
        spans.put(span.getTag("spanid"), span);
    }

    public synchronized DevTrace get(String traceId) {
        Map<String, DevSpanAdapter> spans = traceDB.get(traceId);
        if (spans == null) {
            return null;
        }
        for (DevSpanAdapter span : spans.values()) {
            if (!"true".equals(span.getTag("isDone"))) {
                // Still an active trace, not all spans are closed
                return null;
            }
        }
        traceDB.remove(traceId);
        return new DevTrace(traceId, new ArrayList<>(spans.values()));
    }
}
