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

import java.nio.charset.StandardCharsets;
import java.util.List;

import io.dapr.client.DaprClient;
import io.dapr.client.domain.DeleteStateRequest;
import io.dapr.client.domain.ExecuteStateTransactionRequest;
import io.dapr.client.domain.GetBulkStateRequest;
import io.dapr.client.domain.GetStateRequest;
import io.dapr.client.domain.SaveStateRequest;
import io.dapr.client.domain.State;
import io.dapr.client.domain.TransactionalStateOperation;
import io.dapr.utils.TypeRef;
import org.apache.camel.Exchange;
import org.apache.camel.component.dapr.DaprConfiguration;
import org.apache.camel.component.dapr.DaprConfigurationOptionsProxy;
import org.apache.camel.component.dapr.DaprConstants;
import org.apache.camel.component.dapr.DaprEndpoint;
import org.apache.camel.component.dapr.DaprOperation;
import org.apache.camel.component.dapr.StateOperation;
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
public class DaprStateTest extends CamelTestSupport {

    @Mock
    private DaprClient client;
    @Mock
    private DaprEndpoint endpoint;

    @Test
    void testSave() throws Exception {
        when(endpoint.getClient()).thenReturn(client);
        when(client.saveState(anyString(), anyString(), any(), any(Object.class), any()))
                .thenReturn(Mono.empty());

        DaprConfiguration configuration = new DaprConfiguration();
        configuration.setOperation(DaprOperation.state);
        configuration.setStateOperation(StateOperation.save);
        configuration.setStateStore("myStateStore");
        configuration.setKey("myKey");
        DaprConfigurationOptionsProxy configurationOptionsProxy = new DaprConfigurationOptionsProxy(configuration);

        final Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("myBody");

        final DaprStateHandler operation = new DaprStateHandler(configurationOptionsProxy, endpoint);
        final DaprOperationResponse operationResponse = operation.handle(exchange);

        assertNotNull(operationResponse);
        assertEquals(new State<>("myKey", "myBody", null, null), operationResponse.getBody());
    }

    @Test
    void testSaveConfiguration() {
        DaprConfiguration configuration = new DaprConfiguration();
        configuration.setOperation(DaprOperation.state);
        configuration.setStateOperation(StateOperation.save);
        DaprConfigurationOptionsProxy configurationOptionsProxy = new DaprConfigurationOptionsProxy(configuration);

        final Exchange exchange = new DefaultExchange(context);

        // case 1: stateStore, payload and key empty
        final DaprStateHandler operation = new DaprStateHandler(configurationOptionsProxy, endpoint);
        assertThrows(IllegalArgumentException.class, () -> operation.validateConfiguration(exchange));

        // case 2: payload and key empty
        configuration.setStateStore("myStateStore");
        assertThrows(IllegalArgumentException.class, () -> operation.validateConfiguration(exchange));

        // case 3: payload empty
        configuration.setKey("myKey");
        assertThrows(IllegalArgumentException.class, () -> operation.validateConfiguration(exchange));

        // case 4: valid configuration
        exchange.getIn().setBody("myBody");
        assertDoesNotThrow(() -> operation.validateConfiguration(exchange));
    }

    @Test
    void testSaveBulk() throws Exception {
        when(endpoint.getClient()).thenReturn(client);
        when(client.saveBulkState(any())).thenReturn(Mono.empty());

        DaprConfiguration configuration = new DaprConfiguration();
        configuration.setOperation(DaprOperation.state);
        configuration.setStateOperation(StateOperation.saveBulk);
        configuration.setStateStore("myStateStore");
        DaprConfigurationOptionsProxy configurationOptionsProxy = new DaprConfigurationOptionsProxy(configuration);

        final Exchange exchange = new DefaultExchange(context);
        State<String> state1 = new State<>("k1");
        State<String> state2 = new State<>("k2");
        List<State<?>> states = List.of(state1, state2);
        exchange.getMessage().setHeader(DaprConstants.STATES, states);

        final DaprStateHandler operation = new DaprStateHandler(configurationOptionsProxy, endpoint);
        final DaprOperationResponse operationResponse = operation.handle(exchange);
        SaveStateRequest saveStateRequest = (SaveStateRequest) operationResponse.getBody();

        assertNotNull(operationResponse);
        assertEquals(states, saveStateRequest.getStates());
    }

    @Test
    void testSaveBulkConfiguration() {
        DaprConfiguration configuration = new DaprConfiguration();
        configuration.setOperation(DaprOperation.state);
        configuration.setStateOperation(StateOperation.saveBulk);
        DaprConfigurationOptionsProxy configurationOptionsProxy = new DaprConfigurationOptionsProxy(configuration);

        final Exchange exchange = new DefaultExchange(context);

        // case 1: stateStore and states empty
        final DaprStateHandler operation = new DaprStateHandler(configurationOptionsProxy, endpoint);
        assertThrows(IllegalArgumentException.class, () -> operation.validateConfiguration(exchange));

        // case 2: states empty
        configuration.setStateStore("myStateStore");
        assertThrows(IllegalArgumentException.class, () -> operation.validateConfiguration(exchange));

        // case 3: valid configuration
        exchange.getIn().setHeader(DaprConstants.STATES, List.of(new State<>("k1")));
        assertDoesNotThrow(() -> operation.validateConfiguration(exchange));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testGet() throws Exception {
        State<byte[]> mockResult = new State<>("myKey", "myValue".getBytes(StandardCharsets.UTF_8), null, null);

        when(endpoint.getClient()).thenReturn(client);
        when(client.getState(any(GetStateRequest.class), eq(TypeRef.get(byte[].class)))).thenReturn(Mono.just(mockResult));

        DaprConfiguration configuration = new DaprConfiguration();
        configuration.setOperation(DaprOperation.state);
        configuration.setStateOperation(StateOperation.get);
        configuration.setStateStore("myStateStore");
        configuration.setKey("myKey");
        DaprConfigurationOptionsProxy configurationOptionsProxy = new DaprConfigurationOptionsProxy(configuration);

        final Exchange exchange = new DefaultExchange(context);

        final DaprStateHandler operation = new DaprStateHandler(configurationOptionsProxy, endpoint);
        final DaprOperationResponse operationResponse = operation.handle(exchange);
        State<byte[]> saveStateRequest = (State<byte[]>) operationResponse.getBody();

        assertNotNull(operationResponse);
        assertEquals(mockResult, saveStateRequest);
    }

    @Test
    void testGetConfiguration() {
        DaprConfiguration configuration = new DaprConfiguration();
        configuration.setOperation(DaprOperation.state);
        configuration.setStateOperation(StateOperation.get);
        DaprConfigurationOptionsProxy configurationOptionsProxy = new DaprConfigurationOptionsProxy(configuration);

        final Exchange exchange = new DefaultExchange(context);

        // case 1: stateStore and key empty
        final DaprStateHandler operation = new DaprStateHandler(configurationOptionsProxy, endpoint);
        assertThrows(IllegalArgumentException.class, () -> operation.validateConfiguration(exchange));

        // case 2: key empty
        configuration.setStateStore("myStateStore");
        assertThrows(IllegalArgumentException.class, () -> operation.validateConfiguration(exchange));

        // case 3: valid configuration
        configuration.setKey("myKey");
        assertDoesNotThrow(() -> operation.validateConfiguration(exchange));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testGetBulk() throws Exception {
        List<State<byte[]>> mockResult = List.of(new State<>("myKey", "myValue".getBytes(StandardCharsets.UTF_8), null, null));

        when(endpoint.getClient()).thenReturn(client);
        when(client.getBulkState(any(GetBulkStateRequest.class), eq(TypeRef.get(byte[].class))))
                .thenReturn(Mono.just(mockResult));

        DaprConfiguration configuration = new DaprConfiguration();
        configuration.setOperation(DaprOperation.state);
        configuration.setStateOperation(StateOperation.getBulk);
        configuration.setStateStore("myStateStore");
        DaprConfigurationOptionsProxy configurationOptionsProxy = new DaprConfigurationOptionsProxy(configuration);

        final Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader(DaprConstants.KEYS, List.of("myKey"));

        final DaprStateHandler operation = new DaprStateHandler(configurationOptionsProxy, endpoint);
        final DaprOperationResponse operationResponse = operation.handle(exchange);
        List<State<byte[]>> getStateRequest = (List<State<byte[]>>) operationResponse.getBody();

        assertNotNull(operationResponse);
        assertEquals(mockResult, getStateRequest);
    }

    @Test
    void testGetBulkConfiguration() {
        DaprConfiguration configuration = new DaprConfiguration();
        configuration.setOperation(DaprOperation.state);
        configuration.setStateOperation(StateOperation.getBulk);
        DaprConfigurationOptionsProxy configurationOptionsProxy = new DaprConfigurationOptionsProxy(configuration);

        final Exchange exchange = new DefaultExchange(context);

        // case 1: stateStore and keys empty
        final DaprStateHandler operation = new DaprStateHandler(configurationOptionsProxy, endpoint);
        assertThrows(IllegalArgumentException.class, () -> operation.validateConfiguration(exchange));

        // case 2: keys empty
        configuration.setStateStore("myStateStore");
        assertThrows(IllegalArgumentException.class, () -> operation.validateConfiguration(exchange));

        // case 3: valid configuration
        exchange.getIn().setHeader(DaprConstants.KEYS, List.of("myKey"));
        assertDoesNotThrow(() -> operation.validateConfiguration(exchange));
    }

    @Test
    void testDelete() throws Exception {
        when(endpoint.getClient()).thenReturn(client);
        when(client.deleteState(any(DeleteStateRequest.class))).thenReturn(Mono.empty());

        DaprConfiguration configuration = new DaprConfiguration();
        configuration.setOperation(DaprOperation.state);
        configuration.setStateOperation(StateOperation.delete);
        configuration.setStateStore("myStateStore");
        configuration.setKey("myKey");
        DaprConfigurationOptionsProxy configurationOptionsProxy = new DaprConfigurationOptionsProxy(configuration);

        final Exchange exchange = new DefaultExchange(context);

        final DaprStateHandler operation = new DaprStateHandler(configurationOptionsProxy, endpoint);
        final DaprOperationResponse operationResponse = operation.handle(exchange);
        DeleteStateRequest deleteStateRequest = (DeleteStateRequest) operationResponse.getBody();

        assertNotNull(operationResponse);
        assertEquals("myStateStore", deleteStateRequest.getStateStoreName());
        assertEquals("myKey", deleteStateRequest.getKey());
    }

    @Test
    void testDeleteConfiguration() {
        DaprConfiguration configuration = new DaprConfiguration();
        configuration.setOperation(DaprOperation.state);
        configuration.setStateOperation(StateOperation.delete);
        DaprConfigurationOptionsProxy configurationOptionsProxy = new DaprConfigurationOptionsProxy(configuration);

        final Exchange exchange = new DefaultExchange(context);

        // case 1: stateStore and key empty
        final DaprStateHandler operation = new DaprStateHandler(configurationOptionsProxy, endpoint);
        assertThrows(IllegalArgumentException.class, () -> operation.validateConfiguration(exchange));

        // case 2: key empty
        configuration.setStateStore("myStateStore");
        assertThrows(IllegalArgumentException.class, () -> operation.validateConfiguration(exchange));

        // case 3: valid configuration
        configuration.setKey("myKey");
        assertDoesNotThrow(() -> operation.validateConfiguration(exchange));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testExecuteTransaction() throws Exception {
        when(endpoint.getClient()).thenReturn(client);
        when(client.executeStateTransaction(any(ExecuteStateTransactionRequest.class))).thenReturn(Mono.empty());

        DaprConfiguration configuration = new DaprConfiguration();
        configuration.setOperation(DaprOperation.state);
        configuration.setStateOperation(StateOperation.executeTransaction);
        configuration.setStateStore("myStateStore");
        DaprConfigurationOptionsProxy configurationOptionsProxy = new DaprConfigurationOptionsProxy(configuration);

        final Exchange exchange = new DefaultExchange(context);
        TransactionalStateOperation.OperationType op = TransactionalStateOperation.OperationType.UPSERT;
        List<TransactionalStateOperation<?>> transactions
                = List.of(new TransactionalStateOperation<>(op, new State<>("myKey")));
        exchange.getIn().setHeader(DaprConstants.TRANSACTIONS, transactions);

        final DaprStateHandler operation = new DaprStateHandler(configurationOptionsProxy, endpoint);
        final DaprOperationResponse operationResponse = operation.handle(exchange);
        ExecuteStateTransactionRequest executeRequest = (ExecuteStateTransactionRequest) operationResponse.getBody();

        assertNotNull(operationResponse);
        assertEquals("myStateStore", executeRequest.getStateStoreName());
        assertEquals(transactions, executeRequest.getOperations());
    }

    @Test
    void testExecuteTransactionConfiguration() {
        DaprConfiguration configuration = new DaprConfiguration();
        configuration.setOperation(DaprOperation.state);
        configuration.setStateOperation(StateOperation.executeTransaction);
        DaprConfigurationOptionsProxy configurationOptionsProxy = new DaprConfigurationOptionsProxy(configuration);

        final Exchange exchange = new DefaultExchange(context);

        // case 1: stateStore and transactions empty
        final DaprStateHandler operation = new DaprStateHandler(configurationOptionsProxy, endpoint);
        assertThrows(IllegalArgumentException.class, () -> operation.validateConfiguration(exchange));

        // case 2: transactions empty
        configuration.setStateStore("myStateStore");
        assertThrows(IllegalArgumentException.class, () -> operation.validateConfiguration(exchange));

        // case 3: valid configuration
        TransactionalStateOperation.OperationType op = TransactionalStateOperation.OperationType.UPSERT;
        List<TransactionalStateOperation<?>> transactions
                = List.of(new TransactionalStateOperation<>(op, new State<>("myKey")));
        exchange.getIn().setHeader(DaprConstants.TRANSACTIONS, transactions);
        assertDoesNotThrow(() -> operation.validateConfiguration(exchange));
    }
}
