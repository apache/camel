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
package org.apache.camel.component.alibaba.fc;

import com.aliyun.fc_open20210406.Client;
import com.aliyun.fc_open20210406.models.GetFunctionRequest;
import com.aliyun.fc_open20210406.models.GetFunctionResponse;
import com.aliyun.fc_open20210406.models.InvokeFunctionRequest;
import com.aliyun.fc_open20210406.models.InvokeFunctionResponse;
import org.apache.camel.Exchange;
import org.apache.camel.component.alibaba.fc.constants.FCHeaders;
import org.apache.camel.component.alibaba.fc.constants.FCOperations;
import org.apache.camel.component.alibaba.fc.models.ClientConfigurations;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;

public class FCProducer extends DefaultProducer {

    private Client fcClient;

    public FCProducer(FCEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        FCEndpoint endpoint = getEndpoint();
        ClientConfigurations configuration = FCUtils.createClientConfigurations(endpoint, exchange);

        if (ObjectHelper.isEmpty(configuration.getOperation())) {
            throw new IllegalArgumentException("Operation name not found");
        }

        if (fcClient == null) {
            fcClient = endpoint.initClient();
        }

        switch (configuration.getOperation()) {
            case FCOperations.INVOKE_FUNCTION -> invokeFunction(exchange, configuration);
            case FCOperations.GET_FUNCTION -> getFunction(exchange, configuration);
            default -> throw new UnsupportedOperationException("Unsupported operation: " + configuration.getOperation());
        }
    }

    private void invokeFunction(Exchange exchange, ClientConfigurations configuration) throws Exception {
        validateServiceAndFunction(configuration);

        InvokeFunctionRequest request = new InvokeFunctionRequest()
                .setBody(FCUtils.resolvePayload(exchange));
        if (ObjectHelper.isNotEmpty(configuration.getQualifier())) {
            request.setQualifier(configuration.getQualifier());
        }

        InvokeFunctionResponse response = fcClient.invokeFunction(
                configuration.getServiceName(),
                configuration.getFunctionName(),
                request);

        exchange.getMessage().setBody(FCUtils.toInvokeFunctionMap(response));
        if (response.getStatusCode() != null) {
            exchange.getMessage().setHeader(FCHeaders.STATUS_CODE, response.getStatusCode());
        }
        if (response.getHeaders() != null && ObjectHelper.isNotEmpty(response.getHeaders().get("x-fc-request-id"))) {
            exchange.getMessage().setHeader(FCHeaders.REQUEST_ID, response.getHeaders().get("x-fc-request-id"));
        }
    }

    private void getFunction(Exchange exchange, ClientConfigurations configuration) throws Exception {
        validateServiceAndFunction(configuration);

        GetFunctionRequest request = new GetFunctionRequest();
        if (ObjectHelper.isNotEmpty(configuration.getQualifier())) {
            request.setQualifier(configuration.getQualifier());
        }

        GetFunctionResponse response = fcClient.getFunction(
                configuration.getServiceName(),
                configuration.getFunctionName(),
                request);

        exchange.getMessage().setBody(FCUtils.toGetFunctionMap(response.getBody()));
        if (response.getStatusCode() != null) {
            exchange.getMessage().setHeader(FCHeaders.STATUS_CODE, response.getStatusCode());
        }
    }

    private void validateServiceAndFunction(ClientConfigurations configuration) {
        if (ObjectHelper.isEmpty(configuration.getServiceName()) || ObjectHelper.isEmpty(configuration.getFunctionName())) {
            throw new IllegalArgumentException("Service name and function name are required");
        }
    }

    @Override
    public FCEndpoint getEndpoint() {
        return (FCEndpoint) super.getEndpoint();
    }
}
