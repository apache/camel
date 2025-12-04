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
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;

public class DevTrace implements Iterable<DevSpanAdapter> {

    private String traceId;
    private List<DevSpanAdapter> spans;

    DevTrace() {}

    public DevTrace(String traceId, List<DevSpanAdapter> spans) {
        this.traceId = traceId;
        this.spans = spans;
        Collections.sort(this.spans, new SpanComparator());
    }

    @Override
    public String toString() {
        return traceId + " " + spans.toString();
    }

    public String getTraceId() {
        return this.traceId;
    }

    void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public List<DevSpanAdapter> getSpans() {
        return this.spans;
    }

    public List<DevSpanAdapter> sortSpans() {
        List<DevSpanAdapter> spans = new ArrayList<>();
        for (DevSpanAdapter span : this) {
            spans.add(span);
        }
        return spans;
    }

    void setSpans(List<DevSpanAdapter> spans) {
        this.spans = spans;
    }

    @Override
    public Iterator<DevSpanAdapter> iterator() {
        return new DevSpanAdapterIterator();
    }

    class DevSpanAdapterIterator implements Iterator<DevSpanAdapter> {

        Stack<DevSpanAdapter> actual = new Stack<>();
        private HashMap<String, Boolean> scanned;

        DevSpanAdapterIterator() {
            this.scanned = new HashMap<>();
            this.actual = new Stack<>();
        }

        @Override
        public boolean hasNext() {
            return scanned.size() < spans.size();
        }

        @Override
        public DevSpanAdapter next() {
            DevSpanAdapter next;
            if (actual.empty()) {
                next = getWithParent(null);
            } else {
                next = getWithParent(actual.peek().getSpanId());
            }
            while (next == null && !actual.empty()) {
                // it's a leaf, let's find out the upper branch
                DevSpanAdapter upperLevel = actual.pop();
                next = getWithParent(upperLevel.getParentSpanId());
            }

            if (next != null) {
                actual.push(next);
                scanned.put(next.getSpanId(), true);
            }
            return next;
        }

        private DevSpanAdapter getWithParent(String parentSpanId) {
            for (DevSpanAdapter span : spans) {
                if (parentSpanId == null && span.getParentSpanId() == null && !scanned.containsKey(span.getSpanId())) {
                    return span;
                }
                if (span.getParentSpanId() != null
                        && span.getParentSpanId().equals(parentSpanId)
                        && !scanned.containsKey(span.getSpanId())) {
                    return span;
                }
            }

            return null;
        }
    }
}

class SpanComparator implements java.util.Comparator<DevSpanAdapter> {
    @Override
    public int compare(DevSpanAdapter a, DevSpanAdapter b) {
        DevSpanAdapter msa = (DevSpanAdapter) a;
        DevSpanAdapter msb = (DevSpanAdapter) b;
        return (int) (Long.parseLong(msa.getTag("initTimestamp")) - Long.parseLong(msb.getTag("initTimestamp")));
    }
}
