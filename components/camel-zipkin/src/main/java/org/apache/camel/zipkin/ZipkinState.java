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

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

import brave.Span;
import org.apache.camel.Exchange;
import org.apache.camel.SafeCopyProperty;

/**
 * The state of the zipkin trace which we store on the {@link Exchange}
 * <p/>
 * This is needed to keep track of of correlating when an existing span is calling downstream service(s) and therefore
 * must be able to correlate those service calls with the parent span.
 */
public final class ZipkinState implements SafeCopyProperty {

    public static final String KEY = "CamelZipkinState";

    private final Deque<Span> clientSpans = new ConcurrentLinkedDeque<>();
    private final Deque<Span> serverSpans = new ConcurrentLinkedDeque<>();

    public ZipkinState() {

    }

    private ZipkinState(ZipkinState state) {
        this.clientSpans.addAll(state.clientSpans);
        this.serverSpans.addAll(state.serverSpans);
    }

    public void pushClientSpan(Span span) {
        clientSpans.push(span);
    }

    public Span popClientSpan() {
        if (!clientSpans.isEmpty()) {
            return clientSpans.pop();
        } else {
            return null;
        }
    }

    public void pushServerSpan(Span span) {
        serverSpans.push(span);
    }

    public Span popServerSpan() {
        if (!serverSpans.isEmpty()) {
            return serverSpans.pop();
        } else {
            return null;
        }
    }

    public Span peekServerSpan() {
        if (!serverSpans.isEmpty()) {
            return serverSpans.peek();
        } else {
            return null;
        }
    }

    @Override
    public ZipkinState safeCopy() {
        return new ZipkinState(this);
    }

}
