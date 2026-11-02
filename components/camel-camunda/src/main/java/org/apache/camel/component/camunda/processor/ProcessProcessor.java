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
import org.apache.camel.component.camunda.model.ProcessRequest;
import org.apache.camel.component.camunda.model.ProcessResponse;

public class ProcessProcessor extends AbstractBaseProcessor {
    public ProcessProcessor(CamundaEndpoint endpoint) {
        super(endpoint);
    }

    public void process(Exchange exchange) throws Exception {
        ProcessRequest message = null;

        Object body = exchange.getMessage().getBody();
        if (body instanceof ProcessRequest) {
            message = (ProcessRequest) body;
        } else if (body instanceof ProcessResponse) {
            ProcessResponse response = (ProcessResponse) body;
            message = new ProcessRequest();
            message.setProcessInstanceKey(response.getProcessInstanceKey());
            message.setProcessId(response.getProcessId());
            message.setProcessVersion(response.getProcessVersion());
            message.setProcessKey(response.getProcessKey());
        } else if (body != null) {
            message = readJson(exchange, ProcessRequest.class, "Cannot convert body to ProcessMessage");
        }
        if (message == null) {
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

        setBody(exchange, resultMessage);
        failIfNotSuccess(exchange, resultMessage);
    }

    private ProcessResponse startProcess(ProcessRequest processMessage) {
        CamundaService camundaService = endpoint.getComponent().getCamundaService();
        return camundaService.startProcess(processMessage);
    }

    private ProcessResponse cancelProcess(ProcessRequest processMessage) {
        CamundaService camundaService = endpoint.getComponent().getCamundaService();
        return camundaService.cancelProcessInstance(processMessage);
    }
}
