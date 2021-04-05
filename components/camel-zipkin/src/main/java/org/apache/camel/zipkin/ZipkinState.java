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
package org.apache.camel.zipkin;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

import brave.Span;
import brave.propagation.TraceContextOrSamplingFlags;
import org.apache.camel.Exchange;

/**
 * The state of the zipkin trace which we store on the {@link Exchange}
 * <p/>
 * This is needed to keep track of of correlating when an existing span is calling downstream service(s) and therefore
 * must be able to correlate those service calls with the parent span.
 */
public final class ZipkinState {

    public static final String KEY = "CamelZipkinState";

    private final Deque<Span> clientSpans = new ArrayDeque<>();
    private final Deque<Span> serverSpans = new ArrayDeque<>();

    public synchronized void pushClientSpan(Span span) {
        clientSpans.push(span);
    }

    public synchronized Span popClientSpan() {
        if (!clientSpans.isEmpty()) {
            return clientSpans.pop();
        } else {
            return null;
        }
    }

    public synchronized void pushServerSpan(Span span) {
        serverSpans.push(span);
    }

    public synchronized Span popServerSpan() {
        if (!serverSpans.isEmpty()) {
            return serverSpans.pop();
        } else {
            return null;
        }
    }

    private Span peekServerSpan() {
        if (!serverSpans.isEmpty()) {
            return serverSpans.peek();
        } else {
            return null;
        }
    }

    public synchronized Span findMatchingServerSpan(Exchange exchange) {
        String spanId = (String) exchange.getIn().getHeader(ZipkinConstants.SPAN_ID);
        Span lastSpan = peekServerSpan();
        if (spanId == null) {
            return lastSpan;
        }
        TraceContextOrSamplingFlags traceContext
                = ZipkinTracer.EXTRACTOR.extract(new CamelRequest(exchange.getIn(), Span.Kind.SERVER));
        if (traceContext.context().spanId() == lastSpan.context().spanId()) {
            return lastSpan;
        }

        Iterator<Span> spanItr = serverSpans.iterator();
        while (spanItr.hasNext()) {
            Span span = spanItr.next();
            if (span.context().spanId() == traceContext.context().spanId()) {
                return span;
            }
        }
        return lastSpan;
    }

}
