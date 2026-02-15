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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DaprInvokeBindingTest extends CamelTestSupport {

    @Mock
    private DaprClient client;
    @Mock
    private DaprEndpoint endpoint;

    @Test
    void testInvokeBinding() throws Exception {
        final byte[] mockResponse = "hello".getBytes();

        when(endpoint.getClient()).thenReturn(client);
        when(client.invokeBinding(any(), any())).thenReturn(Mono.just(mockResponse));

        DaprConfiguration configuration = new DaprConfiguration();
        configuration.setOperation(DaprOperation.invokeBinding);
        configuration.setBindingName("myBinding");
        configuration.setBindingOperation("myOperation");
        DaprConfigurationOptionsProxy configurationOptionsProxy = new DaprConfigurationOptionsProxy(configuration);

        final Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("myBody");

        final DaprInvokeBindingHandler operation = new DaprInvokeBindingHandler(configurationOptionsProxy, endpoint);
        final DaprOperationResponse operationResponse = operation.handle(exchange);

        assertNotNull(operationResponse);
        assertEquals(mockResponse, operationResponse.getBody());
    }

    @Test
    void testValidateConfiguration() {
        DaprConfiguration configuration = new DaprConfiguration();
        configuration.setOperation(DaprOperation.invokeBinding);
        DaprConfigurationOptionsProxy configurationOptionsProxy = new DaprConfigurationOptionsProxy(configuration);

        final Exchange exchange = new DefaultExchange(context);
        final DaprInvokeBindingHandler operation = new DaprInvokeBindingHandler(configurationOptionsProxy, endpoint);

        // case 1: both bindingName and bindingOperation empty
        assertThrows(IllegalArgumentException.class, () -> operation.validateConfiguration(exchange));

        // case 2: only bindingName set
        configuration.setBindingName("myBinding");
        assertThrows(IllegalArgumentException.class, () -> operation.validateConfiguration(exchange));

        // case 3: valid configuration
        configuration.setBindingOperation("myOperation");
        assertDoesNotThrow(() -> operation.validateConfiguration(exchange));
    }
}
