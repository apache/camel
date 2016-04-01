/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.zipkin;

import java.util.Stack;

import com.github.kristofa.brave.ServerSpan;
import com.twitter.zipkin.gen.Span;
import org.apache.camel.Exchange;

/**
 * The state of the zipkin trace which we store on the {@link Exchange}
 * <p/>
 * This is needed to keep track of of correlating when an existing span
 * is calling downstream service(s) and therefore must be able to correlate
 * those service calls with the parent span.
 */
public final class ZipkinState {

    public static final String KEY = "CamelZipkinState";

    private final Stack<Span> clientSpans = new Stack<>();
    private final Stack<ServerSpan> serverSpans = new Stack<>();
    private Object last;

    public void pushClientSpan(Span span) {
        clientSpans.push(span);
        last = span;
    }

    public Span popClientSpan() {
        if (!clientSpans.empty()) {
            Span answer = clientSpans.pop();
            last = answer;
            return answer;
        } else {
            return null;
        }
    }

    public void pushServerSpan(ServerSpan span) {
        serverSpans.push(span);
        last = span;
    }

    public ServerSpan popServerSpan() {
        if (!serverSpans.empty()) {
            ServerSpan answer = serverSpans.pop();
            last = answer;
            return answer;
        } else {
            return null;
        }
    }

    public Object getLast() {
        return last;
    }
}
