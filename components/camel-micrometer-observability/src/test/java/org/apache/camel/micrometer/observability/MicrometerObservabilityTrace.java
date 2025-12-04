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

package org.apache.camel.micrometer.observability;

import java.util.ArrayList;
import java.util.List;

import io.micrometer.tracing.test.simple.SimpleSpan;

/*
 * This class is used for testing purposes only. It groups an array of Spans belonging to the same Trace.
 */
public class MicrometerObservabilityTrace {

    private String traceId;
    private List<SimpleSpan> spans;

    public MicrometerObservabilityTrace(String traceId) {
        this.traceId = traceId;
        this.spans = new ArrayList<>();
    }

    public void add(SimpleSpan span) {
        this.spans.add(span);
    }

    public List<SimpleSpan> getSpans() {
        return this.spans;
    }

    public String getTraceId() {
        return this.traceId;
    }

    public String toString() {
        return "Trace Id: " + traceId + " " + spans;
    }
}
