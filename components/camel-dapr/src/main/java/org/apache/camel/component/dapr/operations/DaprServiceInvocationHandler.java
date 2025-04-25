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

import java.util.List;
import java.util.Map;

import io.dapr.client.DaprClient;
import io.dapr.client.DaprHttp;
import io.dapr.client.domain.HttpExtension;
import org.apache.camel.Exchange;
import org.apache.camel.component.dapr.DaprConfigurationOptionsProxy;
import org.apache.camel.util.ObjectHelper;

public class DaprServiceInvocationHandler implements DaprOperationHandler {

    private final DaprConfigurationOptionsProxy configurationOptionsProxy;

    public DaprServiceInvocationHandler(DaprConfigurationOptionsProxy configurationOptionsProxy) {
        this.configurationOptionsProxy = configurationOptionsProxy;
    }

    @Override
    public DaprOperationResponse handle(Exchange exchange, DaprClient client) {
        Object payload = exchange.getIn().getBody();
        HttpExtension httpExtension = initHttpExtension(exchange);
        String service = configurationOptionsProxy.getServiceToInvoke(exchange);
        String method = configurationOptionsProxy.getMethodToInvoke(exchange);

        final byte[] response = client.invokeMethod(service, method, payload, httpExtension, byte[].class).block();

        return DaprOperationResponse.create(response);
    }

    private HttpExtension initHttpExtension(Exchange exchange) {
        HttpExtension httpExtension = configurationOptionsProxy.getHttpExtension(exchange);

        if (ObjectHelper.isEmpty(httpExtension)) {
            // create HttpExtension from verb, query parameters and headers
            String verb = configurationOptionsProxy.getVerb(exchange);
            Map<String, List<String>> queryParameters = configurationOptionsProxy.getQueryParameters(exchange);
            Map<String, String> httpHeaders = configurationOptionsProxy.getHttpHeaders(exchange);
            httpExtension = new HttpExtension(DaprHttp.HttpMethods.valueOf(verb), queryParameters, httpHeaders);
        }

        return httpExtension;
    }

    @Override
    public void validateConfiguration(Exchange exchange) {
        String serviceToInvoke = configurationOptionsProxy.getServiceToInvoke(exchange);
        String methodToInvoke = configurationOptionsProxy.getMethodToInvoke(exchange);

        if (ObjectHelper.isEmpty(serviceToInvoke) || ObjectHelper.isEmpty(methodToInvoke)) {
            throw new IllegalArgumentException("serviceToInvoke and methodToInvoke are mandatory to invoke a service");
        }
    }
}
