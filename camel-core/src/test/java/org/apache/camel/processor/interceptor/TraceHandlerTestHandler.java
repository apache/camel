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

import java.util.LinkedList;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.model.ProcessorDefinition;

public class TraceHandlerTestHandler implements TraceEventHandler {
    private List<StringBuilder> eventMessages;
    private boolean traceAllNodes;

    public TraceHandlerTestHandler() {
        this.eventMessages = new LinkedList<StringBuilder>();
        this.traceAllNodes = false;
    }

    public TraceHandlerTestHandler(List<StringBuilder> eventMessages) {
        this.eventMessages = eventMessages;
        this.traceAllNodes = false;
    }

    public List<StringBuilder> getEventMessages() {
        return eventMessages;
    }

    public void setEventMessages(List<StringBuilder> eventMessages) {
        this.eventMessages = eventMessages;
    }

    public boolean isTraceAllNodes() {
        return traceAllNodes;
    }

    public void setTraceAllNodes(boolean traceAllNodes) {
        this.traceAllNodes = traceAllNodes;
    }

    private synchronized void storeMessage(StringBuilder message) {
        eventMessages.add(message);
    }

    public static void recordComplete(StringBuilder message, ProcessorDefinition<?> node, Exchange exchange) {
        message.append("Complete: ");
        message.append(node.getLabel() + ": ");
        message.append(exchange.getIn().getBody());
    }

    public static void recordIn(StringBuilder message, ProcessorDefinition<?> node, Exchange exchange) {
        message.append("In: ");
        message.append(node.getLabel() + ": ");
        message.append(exchange.getIn().getBody());
    }

    public static void recordOut(StringBuilder message, ProcessorDefinition<?> node, Exchange exchange) {
        message.append("Out: ");
        message.append(node.getLabel() + ": ");
        if (null != exchange.getOut()) {
            message.append(exchange.getOut().getBody());
        }
        if (null != exchange.getException()) {
            Exception ex = exchange.getException();
            message.append("\t");
            message.append("Ex: ");
            message.append(ex.getMessage());
        }
    }

    public void traceExchange(ProcessorDefinition<?> node, Processor target, TraceInterceptor traceInterceptor, Exchange exchange) throws Exception {
        if (traceAllNodes || !node.getLabel().contains("TraceTestProcessor")) {
            StringBuilder message = new StringBuilder();
            recordComplete(message, node, exchange);
            storeMessage(message);
        }
    }

    public Object traceExchangeIn(ProcessorDefinition<?> node, Processor target, TraceInterceptor traceInterceptor, Exchange exchange) throws Exception {
        if (traceAllNodes || !node.getLabel().contains("TraceTestProcessor")) {
            StringBuilder message = new StringBuilder();
            recordIn(message, node, exchange);
            return message;
        } else {
            return null;
        }
    }

    public void traceExchangeOut(ProcessorDefinition<?> node, Processor target, TraceInterceptor traceInterceptor, Exchange exchange, Object traceState) throws Exception {
        if (traceAllNodes || !node.getLabel().contains("TraceTestProcessor")) {
            if (StringBuilder.class.equals(traceState.getClass())) {
                StringBuilder message = (StringBuilder) traceState;
                recordOut(message, node, exchange);
                storeMessage(message);
            }
        }
    }
}
