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

import java.io.InputStream;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.camel.CamelException;
import org.apache.camel.Exchange;
import org.apache.camel.component.camunda.CamundaConstants;
import org.apache.camel.component.camunda.CamundaEndpoint;
import org.apache.camel.component.camunda.internal.CamundaService;
import org.apache.camel.component.camunda.model.DeploymentRequest;
import org.apache.camel.component.camunda.model.DeploymentResponse;
import org.apache.camel.component.camunda.model.ProcessDeploymentResponse;

public class DeploymentProcessor extends AbstractBaseProcessor {
    public DeploymentProcessor(CamundaEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        DeploymentRequest message = null;

        Object body = exchange.getMessage().getBody();
        String headerResourceName = exchange.getMessage().getHeader(CamundaConstants.RESOURCE_NAME, String.class);
        if (headerResourceName != null && (body instanceof String || body instanceof byte[] || body instanceof InputStream)) {
            message = new DeploymentRequest();
            message.setName(headerResourceName);
            if (body instanceof String) {
                message.setFileContent(((String) body).getBytes());
            } else if (body instanceof byte[]) {
                message.setFileContent((byte[]) body);
            } else {
                message.setFileContent(((InputStream) body).readAllBytes());
            }
        } else if (body instanceof DeploymentRequest) {
            message = (DeploymentRequest) body;
        } else if (body instanceof String) {
            try {
                message = objectMapper.readValue((String) body, DeploymentRequest.class);
            } catch (JsonProcessingException jsonProcessingException) {
                throw new IllegalArgumentException("Cannot convert body to DeploymentRequest", jsonProcessingException);
            }
        } else {
            throw new CamelException("Deployment Resource missing");
        }

        DeploymentResponse resultMessage = null;

        switch (endpoint.getOperationName()) {
            case DEPLOY_RESOURCE:
                resultMessage = deployResource(message);
                break;
            default:
                throw new IllegalArgumentException("Unknown Operation!");
        }

        removeHeaders(exchange);
        setBody(exchange, resultMessage);

        exchange.getMessage().setHeader(CamundaConstants.IS_SUCCESS, resultMessage.isSuccess());
        if (resultMessage.isSuccess()) {
            if (resultMessage instanceof ProcessDeploymentResponse) {
                exchange.getMessage().setHeader(CamundaConstants.RESOURCE_NAME,
                        ((ProcessDeploymentResponse) resultMessage).getResourceName());
                exchange.getMessage().setHeader(CamundaConstants.BPMN_PROCESS_ID,
                        ((ProcessDeploymentResponse) resultMessage).getBpmnProcessId());
                exchange.getMessage().setHeader(CamundaConstants.PROCESS_DEFINITION_KEY,
                        ((ProcessDeploymentResponse) resultMessage).getProcessDefinitionKey());
                exchange.getMessage().setHeader(CamundaConstants.VERSION,
                        ((ProcessDeploymentResponse) resultMessage).getVersion());
            }
        } else {
            exchange.getMessage().setHeader(CamundaConstants.ERROR_MESSAGE, resultMessage.getErrorMessage());
            exchange.getMessage().setHeader(CamundaConstants.ERROR_CODE, resultMessage.getErrorCode());
        }
    }

    private DeploymentResponse deployResource(DeploymentRequest message) {
        CamundaService camundaService = endpoint.getComponent().getCamundaService();
        return camundaService.deployResource(message);
    }
}
