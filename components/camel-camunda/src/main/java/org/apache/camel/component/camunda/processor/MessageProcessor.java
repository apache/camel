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
package org.apache.camel.component.camunda.processor;

import org.apache.camel.CamelException;
import org.apache.camel.Exchange;
import org.apache.camel.component.camunda.CamundaEndpoint;
import org.apache.camel.component.camunda.internal.CamundaService;
import org.apache.camel.component.camunda.model.MessageRequest;
import org.apache.camel.component.camunda.model.MessageResponse;

public class MessageProcessor extends AbstractBaseProcessor {

    public MessageProcessor(CamundaEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        MessageRequest message = null;

        Object body = exchange.getMessage().getBody();
        if (body instanceof MessageRequest) {
            message = (MessageRequest) body;
        } else if (body != null) {
            message = readJson(exchange, MessageRequest.class, "Cannot convert body to MessageRequest");
        }
        if (message == null) {
            throw new CamelException("Message data missing");
        }

        MessageResponse resultMessage = null;

        switch (endpoint.getOperationName()) {
            case PUBLISH_MESSAGE:
                resultMessage = publishMessage(message);
                break;
            default:
                throw new IllegalArgumentException("Unknown Operation!");
        }

        setBody(exchange, resultMessage);
        failIfNotSuccess(exchange, resultMessage);
    }

    private MessageResponse publishMessage(MessageRequest message) {
        CamundaService camundaService = endpoint.getComponent().getCamundaService();
        return camundaService.publishMessage(message);
    }
}
