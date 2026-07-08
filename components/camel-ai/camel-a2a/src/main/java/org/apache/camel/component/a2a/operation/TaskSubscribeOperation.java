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
package org.apache.camel.component.a2a.operation;

import org.apache.camel.Exchange;
import org.apache.camel.component.a2a.A2AConstants;
import org.apache.camel.component.a2a.model.TaskSubscribeRequest;

/**
 * SubscribeToTask operation. Registers an SSE subscriber on the task store for the given task ID. The consumer creates
 * a {@link org.apache.camel.component.a2a.streaming.DefaultStreamEmitter} and subscribes it to the task's event stream.
 * Events emitted to any of the task's subscribers are forwarded as SSE frames.
 */
public class TaskSubscribeOperation implements A2AOperation {

    @Override
    public A2AOperations getOperationType() {
        return A2AOperations.TASK_SUBSCRIBE;
    }

    @Override
    public boolean isStreaming() {
        return true;
    }

    @Override
    public boolean isInfrastructureOnly() {
        return true;
    }

    @Override
    public Object buildRequest(Exchange exchange) {
        TaskSubscribeRequest request = new TaskSubscribeRequest();
        request.setId(exchange.getMessage().getHeader(A2AConstants.TASK_ID, String.class));
        Integer historyLength = exchange.getMessage().getHeader(A2AConstants.HISTORY_LENGTH, Integer.class);
        if (historyLength != null) {
            request.setHistoryLength(historyLength);
        }
        return request;
    }

    @Override
    public void parseRequest(Exchange exchange, Object request) {
        TaskSubscribeRequest subscribeRequest = (TaskSubscribeRequest) request;
        exchange.getMessage().setHeader(A2AConstants.TASK_ID, subscribeRequest.getId());
        exchange.getMessage().setHeader(A2AConstants.OPERATION, methodName());
        if (subscribeRequest.getHistoryLength() != null) {
            exchange.getMessage().setHeader(A2AConstants.HISTORY_LENGTH, subscribeRequest.getHistoryLength());
        }
    }

    @Override
    public void parseResponse(Exchange exchange, Object response) {
        exchange.getMessage().setBody(response);
        exchange.getMessage().setHeader(A2AConstants.RESPONSE_TYPE, "stream");
    }

    @Override
    public Object buildResponse(Exchange exchange) {
        return exchange.getMessage().getHeader(A2AConstants.STREAM_EMITTER);
    }
}
