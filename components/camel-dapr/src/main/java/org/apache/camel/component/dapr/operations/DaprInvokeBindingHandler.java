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
package org.apache.camel.component.dapr.operations;

import java.util.Map;

import io.dapr.client.DaprClient;
import io.dapr.client.domain.InvokeBindingRequest;
import io.dapr.utils.TypeRef;
import org.apache.camel.Exchange;
import org.apache.camel.component.dapr.DaprConfigurationOptionsProxy;
import org.apache.camel.component.dapr.DaprEndpoint;
import org.apache.camel.util.ObjectHelper;

public class DaprInvokeBindingHandler implements DaprOperationHandler {

    private final DaprConfigurationOptionsProxy configurationOptionsProxy;
    private final DaprEndpoint endpoint;

    public DaprInvokeBindingHandler(DaprConfigurationOptionsProxy configurationOptionsProxy, DaprEndpoint endpoint) {
        this.configurationOptionsProxy = configurationOptionsProxy;
        this.endpoint = endpoint;
    }

    @Override
    public DaprOperationResponse handle(Exchange exchange) {
        Object payload = exchange.getIn().getBody();
        String bindingName = configurationOptionsProxy.getBindingName(exchange);
        String bindingOperation = configurationOptionsProxy.getBindingOperation(exchange);
        Map<String, String> metadata = configurationOptionsProxy.getMetadata(exchange);

        InvokeBindingRequest bindingRequest = new InvokeBindingRequest(bindingName, bindingOperation);
        bindingRequest.setData(payload);
        bindingRequest.setMetadata(metadata);

        DaprClient client = endpoint.getClient();
        final byte[] response = client.invokeBinding(bindingRequest, TypeRef.get(byte[].class)).block();

        return DaprOperationResponse.create(response);
    }

    @Override
    public void validateConfiguration(Exchange exchange) {
        String bindingName = configurationOptionsProxy.getBindingName(exchange);
        String bindingOperation = configurationOptionsProxy.getBindingOperation(exchange);

        if (ObjectHelper.isEmpty(bindingName) || ObjectHelper.isEmpty(bindingOperation)) {
            throw new IllegalArgumentException("bindingName and bindingOperation are mandatory to invoke binding");
        }
    }
}
