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
import org.apache.camel.component.dapr.DaprConfiguration;
import org.apache.camel.component.dapr.DaprConfigurationOptionsProxy;
import org.apache.camel.component.dapr.DaprEndpoint;
import org.apache.camel.component.dapr.DaprOperation;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DaprSecretTest extends CamelTestSupport {

    @Mock
    private DaprClient client;
    @Mock
    private DaprEndpoint endpoint;

    @Test
    void testGetSecret() throws Exception {
        final Map<String, String> mockResponse = Map.of("myKey", "myVal");

        when(endpoint.getClient()).thenReturn(client);
        when(client.getSecret(any(GetSecretRequest.class))).thenReturn(Mono.just(mockResponse));

        DaprConfiguration configuration = new DaprConfiguration();
        configuration.setOperation(DaprOperation.secret);
        configuration.setSecretStore("myStore");
        configuration.setKey("myKey");
        DaprConfigurationOptionsProxy configurationOptionsProxy = new DaprConfigurationOptionsProxy(configuration);

        final Exchange exchange = new DefaultExchange(context);

        final DaprSecretHandler operation = new DaprSecretHandler(configurationOptionsProxy, endpoint);
        final DaprOperationResponse operationResponse = operation.handle(exchange);

        assertNotNull(operationResponse);
        assertEquals(mockResponse, operationResponse.getBody());
    }

    @Test
    void testGetBulkSecret() throws Exception {
        final Map<String, Map<String, String>> mockResponse = Map.of("secretKey1", Map.of("myKey1", "myVal1"),
                "secretKey2", Map.of("myKey2", "myVal2"));

        when(endpoint.getClient()).thenReturn(client);
        when(client.getBulkSecret(any(GetBulkSecretRequest.class))).thenReturn(Mono.just(mockResponse));

        DaprConfiguration configuration = new DaprConfiguration();
        configuration.setOperation(DaprOperation.secret);
        configuration.setSecretStore("myStore");
        DaprConfigurationOptionsProxy configurationOptionsProxy = new DaprConfigurationOptionsProxy(configuration);

        final Exchange exchange = new DefaultExchange(context);

        final DaprSecretHandler operation = new DaprSecretHandler(configurationOptionsProxy, endpoint);
        final DaprOperationResponse operationResponse = operation.handle(exchange);

        assertNotNull(operationResponse);
        assertEquals(mockResponse, operationResponse.getBody());
    }

    @Test
    void testValidateConfiguration() {
        DaprConfiguration configuration = new DaprConfiguration();
        configuration.setOperation(DaprOperation.secret);
        DaprConfigurationOptionsProxy configurationOptionsProxy = new DaprConfigurationOptionsProxy(configuration);

        final Exchange exchange = new DefaultExchange(context);
        final DaprSecretHandler operation = new DaprSecretHandler(configurationOptionsProxy, endpoint);

        // case 1: secretStore is empty
        assertThrows(IllegalArgumentException.class, () -> operation.validateConfiguration(exchange));

        // case 2: valid configuration
        configuration.setSecretStore("myStore");
        assertDoesNotThrow(() -> operation.validateConfiguration(exchange));
    }
}
