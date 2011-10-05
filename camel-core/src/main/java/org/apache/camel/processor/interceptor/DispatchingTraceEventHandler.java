/**
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
package org.apache.camel.processor.interceptor;

import java.util.HashSet;
import java.util.Set;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.model.ProcessorDefinition;

public class DispatchingTraceEventHandler implements TraceEventHandler {
    
    private Set<TraceEventHandler> handlers = new HashSet<TraceEventHandler>();
    
    public void addHandler(TraceEventHandler handler) {
        handlers.add(handler);
    }
    
    public void removeHandler(TraceEventHandler handler) {
        handlers.remove(handler);
    }

    @Override
    public void traceExchange(ProcessorDefinition node, Processor target, TraceInterceptor traceInterceptor, Exchange exchange) throws Exception {
        for (TraceEventHandler handler : handlers) {
            handler.traceExchange(node, target, traceInterceptor, exchange);
        }
    }

    @Override
    public Object traceExchangeIn(ProcessorDefinition node, Processor target, TraceInterceptor traceInterceptor, Exchange exchange) throws Exception {
        Object result = null;
        for (TraceEventHandler handler : handlers) {
            result = handler.traceExchangeIn(node, target, traceInterceptor, exchange);
        }
        return result;
    }

    @Override
    public void traceExchangeOut(ProcessorDefinition node, Processor target, TraceInterceptor traceInterceptor, Exchange exchange, Object traceState) throws Exception {
        for (TraceEventHandler handler : handlers) {
            handler.traceExchangeOut(node, target, traceInterceptor, exchange, traceState);
        }
    }

}
