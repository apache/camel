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

import io.dapr.client.DaprClient;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DaprInvokeServiceTest extends CamelTestSupport {

    @Mock
    private DaprClient client;
    @Mock
    private DaprEndpoint endpoint;

    @Test
    void testInvokeService() throws Exception {
        final byte[] mockResponse = "hello".getBytes();

        when(endpoint.getClient()).thenReturn(client);
        when(client.invokeMethod(anyString(), anyString(), any(Object.class), any(), eq(byte[].class)))
                .thenReturn(Mono.just(mockResponse));

        DaprConfiguration configuration = new DaprConfiguration();
        configuration.setOperation(DaprOperation.invokeService);
        configuration.setServiceToInvoke("myService");
        configuration.setMethodToInvoke("myMethod");
        DaprConfigurationOptionsProxy configurationOptionsProxy = new DaprConfigurationOptionsProxy(configuration);

        final Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("myBody");

        final DaprServiceInvocationHandler operation = new DaprServiceInvocationHandler(configurationOptionsProxy, endpoint);
        final DaprOperationResponse operationResponse = operation.handle(exchange);

        assertNotNull(operationResponse);
        assertEquals(mockResponse, operationResponse.getBody());
    }

    @Test
    void testPositiveValidateConfiguration() {
        DaprConfiguration configuration = new DaprConfiguration();
        configuration.setOperation(DaprOperation.invokeService);
        configuration.setServiceToInvoke("myService");
        configuration.setMethodToInvoke("myMethod");
        DaprConfigurationOptionsProxy configurationOptionsProxy = new DaprConfigurationOptionsProxy(configuration);

        final Exchange exchange = new DefaultExchange(context);

        final DaprServiceInvocationHandler operation = new DaprServiceInvocationHandler(configurationOptionsProxy, endpoint);
        assertDoesNotThrow(() -> operation.validateConfiguration(exchange));
    }

    @Test
    void testNegativeValidateConfiguration() {
        DaprConfiguration configuration = new DaprConfiguration();
        configuration.setOperation(DaprOperation.invokeService);
        // only setting method
        configuration.setMethodToInvoke("myMethod");
        DaprConfigurationOptionsProxy configurationOptionsProxy = new DaprConfigurationOptionsProxy(configuration);

        final Exchange exchange = new DefaultExchange(context);

        final DaprServiceInvocationHandler operation = new DaprServiceInvocationHandler(configurationOptionsProxy, endpoint);
        Exception ex = assertThrows(IllegalArgumentException.class, () -> operation.validateConfiguration(exchange));

        assertEquals("serviceToInvoke and methodToInvoke are mandatory to invoke a service", ex.getMessage());
    }
}
