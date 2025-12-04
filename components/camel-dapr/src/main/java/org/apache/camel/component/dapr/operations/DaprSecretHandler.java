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
import io.dapr.client.domain.GetBulkSecretRequest;
import io.dapr.client.domain.GetSecretRequest;
import org.apache.camel.Exchange;
import org.apache.camel.component.dapr.DaprConfigurationOptionsProxy;
import org.apache.camel.component.dapr.DaprEndpoint;
import org.apache.camel.util.ObjectHelper;

public class DaprSecretHandler implements DaprOperationHandler {

    private final DaprConfigurationOptionsProxy configurationOptionsProxy;
    private final DaprEndpoint endpoint;

    public DaprSecretHandler(DaprConfigurationOptionsProxy configurationOptionsProxy, DaprEndpoint endpoint) {
        this.configurationOptionsProxy = configurationOptionsProxy;
        this.endpoint = endpoint;
    }

    @Override
    public DaprOperationResponse handle(Exchange exchange) {
        String secretStore = configurationOptionsProxy.getSecretStore(exchange);
        String key = configurationOptionsProxy.getKey(exchange);
        Map<String, String> metadata = configurationOptionsProxy.getMetadata(exchange);
        DaprClient client = endpoint.getClient();

        Object response;
        if (ObjectHelper.isNotEmpty(key)) {
            GetSecretRequest secretRequest = new GetSecretRequest(secretStore, key);
            secretRequest.setMetadata(metadata);
            response = client.getSecret(secretRequest).block();
        } else {
            GetBulkSecretRequest secretRequest = new GetBulkSecretRequest(secretStore);
            secretRequest.setMetadata(metadata);
            response = client.getBulkSecret(secretRequest).block();
        }

        return DaprOperationResponse.create(response);
    }

    @Override
    public void validateConfiguration(Exchange exchange) {
        String secretStore = configurationOptionsProxy.getSecretStore(exchange);

        if (ObjectHelper.isEmpty(secretStore)) {
            throw new IllegalArgumentException("secretStore is mandatory to get secret");
        }
    }
}
