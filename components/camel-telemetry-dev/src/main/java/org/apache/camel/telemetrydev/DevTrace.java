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

import java.util.Collections;
import java.util.List;

public class DevTrace {

    private String traceId;
    private List<DevSpanAdapter> spans;

    DevTrace() {

    }

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

    void setSpans(List<DevSpanAdapter> spans) {
        this.spans = spans;
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
