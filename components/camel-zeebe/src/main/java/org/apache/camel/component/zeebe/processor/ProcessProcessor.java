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

package org.apache.camel.component.zeebe.processor;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.camel.CamelException;
import org.apache.camel.Exchange;
import org.apache.camel.component.zeebe.ZeebeEndpoint;
import org.apache.camel.component.zeebe.internal.ZeebeService;
import org.apache.camel.component.zeebe.model.ProcessRequest;
import org.apache.camel.component.zeebe.model.ProcessResponse;

public class ProcessProcessor extends AbstractBaseProcessor {
    public ProcessProcessor(ZeebeEndpoint endpoint) {
        super(endpoint);
    }

    public void process(Exchange exchange) throws Exception {
        ProcessRequest message = null;

        if (exchange.getMessage().getBody() instanceof ProcessRequest) {
            message = exchange.getMessage().getBody(ProcessRequest.class);
        } else if (exchange.getMessage().getBody() instanceof ProcessResponse) {
            message = new ProcessRequest();
            message.setProcessInstanceKey(((ProcessResponse) exchange.getMessage().getBody()).getProcessInstanceKey());
            message.setProcessId(((ProcessResponse) exchange.getMessage().getBody()).getProcessId());
            message.setProcessVersion(((ProcessResponse) exchange.getMessage().getBody()).getProcessVersion());
            message.setProcessKey(((ProcessResponse) exchange.getMessage().getBody()).getProcessKey());
        } else if (exchange.getMessage().getBody() instanceof String) {
            try {
                String bodyString = exchange.getMessage().getBody(String.class);

                message = objectMapper.readValue(bodyString, ProcessRequest.class);
            } catch (JsonProcessingException jsonProcessingException) {
                throw new IllegalArgumentException("Cannot convert body to ProcessMessage", jsonProcessingException);
            }
        } else {
            throw new CamelException("Process data missing");
        }

        ProcessResponse resultMessage = null;

        switch (endpoint.getOperationName()) {
            case START_PROCESS:
                resultMessage = startProcess(message);
                break;
            case CANCEL_PROCESS:
                resultMessage = cancelProcess(message);
                break;
            default:
                throw new IllegalArgumentException("Unknown Operation!");
        }

        setBody(exchange, resultMessage, endpoint.isFormatJSON());
    }

    private ProcessResponse startProcess(ProcessRequest processMessage) {
        ZeebeService zeebeService = endpoint.getComponent().getZeebeService();

        return zeebeService.startProcess(processMessage);
    }

    private ProcessResponse cancelProcess(ProcessRequest processMessage) {
        ZeebeService zeebeService = endpoint.getComponent().getZeebeService();

        return zeebeService.cancelProcessInstance(processMessage);
    }
}
