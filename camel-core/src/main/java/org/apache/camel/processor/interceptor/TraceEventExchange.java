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

import java.util.Date;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.impl.DefaultExchange;

/**
 * Represents a trace of an {@link org.apache.camel.Exchange}, intercepted at the given node
 * that occured during routing.
 * <p/>
 * The IN body contains {@link DefaultTraceEventMessage} with trace details of the original IN message.
 */
public class TraceEventExchange extends DefaultExchange {
    private String nodeId;
    private Date timestamp;
    private Exchange tracedExchange;

    public TraceEventExchange(Exchange parent) {
        super(parent);
    }

    @Override
    public Exchange newInstance() {
        TraceEventExchange answer = new TraceEventExchange(this);
        answer.setNodeId(nodeId);
        answer.setTimestamp(timestamp);
        answer.setTracedExchange(tracedExchange);
        return answer;
    }

    /**
     * Get the id of the node of the trace interception
     */
    public String getNodeId() {
        return nodeId;
    }

    /**
     * Timestamp of the interception
     */
    public Date getTimestamp() {
        return timestamp;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    public Exchange getTracedExchange() {
        return tracedExchange;
    }

    public void setTracedExchange(Exchange tracedExchange) {
        this.tracedExchange = tracedExchange;
    }

    @Override
    public ExchangePattern getPattern() {
        return ExchangePattern.InOnly;
    }

    @Override
    public String toString() {
        return "TraceEventExchange[" + tracedExchange.getExchangeId() + "] on node id: " + nodeId;
    }
}
