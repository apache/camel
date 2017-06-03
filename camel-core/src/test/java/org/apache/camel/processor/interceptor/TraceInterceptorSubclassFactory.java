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

import java.util.List;

import org.apache.camel.DelegateProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.model.ProcessorDefinition;

public class TraceInterceptorSubclassFactory implements TraceInterceptorFactory {
    private List<StringBuilder> eventMessages;
    private boolean traceAllNodes;

    public TraceInterceptorSubclassFactory(List<StringBuilder> eventMessages) {
        this.eventMessages = eventMessages;
    }

    public TraceInterceptor createTraceInterceptor(ProcessorDefinition<?> node, Processor target, TraceFormatter formatter, Tracer tracer) {
        return new TracerInterceptorSubclass(node, target, formatter, tracer, eventMessages, this);
    }

    public boolean getTraceAllNodes() {
        return traceAllNodes;
    }

    public void setTraceAllNodes(boolean traceAllNodes) {
        this.traceAllNodes = traceAllNodes;
    }

    private static class TracerInterceptorSubclass extends TraceInterceptor {
        private List<StringBuilder> eventMessages;
        private boolean traceThisNode = true;
        private TraceInterceptorSubclassFactory factory;

        TracerInterceptorSubclass(ProcessorDefinition<?> node, Processor target, TraceFormatter formatter,
                                         Tracer tracer, List<StringBuilder> eventMessages, TraceInterceptorSubclassFactory factory) {
            super(node, target, formatter, tracer);
            this.eventMessages = eventMessages;
            this.factory = factory;
            while (target instanceof DelegateProcessor) {
                target = ((DelegateProcessor) target).getProcessor();
            }
            if (target.getClass().equals(TraceTestProcessor.class)) {
                traceThisNode = false;
            }
        }

        private synchronized void storeMessage(StringBuilder message) {
            eventMessages.add(message);
        }

        @Override
        protected void traceExchange(Exchange exchange) throws Exception {
            if (traceThisNode || factory.getTraceAllNodes()) {
                StringBuilder message = new StringBuilder();
                TraceHandlerTestHandler.recordComplete(message, getNode(), exchange);
                storeMessage(message);
            }
        }

        @Override
        protected Object traceExchangeIn(Exchange exchange) throws Exception {
            if (traceThisNode || factory.getTraceAllNodes()) {
                StringBuilder message = new StringBuilder();
                TraceHandlerTestHandler.recordIn(message, getNode(), exchange);
                return message;
            } else {
                return null;
            }
        }

        @Override
        protected void traceExchangeOut(Exchange exchange, Object traceState) throws Exception {
            if (traceThisNode || factory.getTraceAllNodes()) {
                if (StringBuilder.class.equals(traceState.getClass())) {
                    StringBuilder message = (StringBuilder) traceState;
                    TraceHandlerTestHandler.recordOut(message, getNode(), exchange);
                    storeMessage(message);
                }
            }
        }
    }

}