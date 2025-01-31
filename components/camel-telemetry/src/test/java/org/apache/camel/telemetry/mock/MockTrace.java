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
import java.util.Collections;
import java.util.List;

import org.apache.camel.telemetry.Span;

public class MockTrace {
    private List<Span> spanList;

    public MockTrace() {
        spanList = new ArrayList<>();
    }

    public void addSpan(Span span) {
        spanList.add(span);
    }

    public List<Span> spans() {
        Collections.sort(spanList, new SpanComparator());
        return spanList;
    }

    @Override
    public String toString() {
        return spans().toString();
    }

}

class SpanComparator implements java.util.Comparator<Span> {
    @Override
    public int compare(Span a, Span b) {
        // cast to get timestamp without changing the Span interface
        MockSpanAdapter msa = (MockSpanAdapter) a;
        MockSpanAdapter msb = (MockSpanAdapter) b;
        return (int) (Long.parseLong(msa.getTag("initTimestamp")) - Long.parseLong(msb.getTag("initTimestamp")));
    }
}
