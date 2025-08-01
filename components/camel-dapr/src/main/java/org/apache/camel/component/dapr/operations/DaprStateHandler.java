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
import io.dapr.client.domain.DeleteStateRequest;
import io.dapr.client.domain.ExecuteStateTransactionRequest;
import io.dapr.client.domain.GetBulkStateRequest;
import io.dapr.client.domain.GetStateRequest;
import io.dapr.client.domain.SaveStateRequest;
import io.dapr.client.domain.State;
import io.dapr.client.domain.StateOptions;
import io.dapr.client.domain.TransactionalStateOperation;
import io.dapr.utils.TypeRef;
import org.apache.camel.Exchange;
import org.apache.camel.component.dapr.DaprConfigurationOptionsProxy;
import org.apache.camel.component.dapr.DaprEndpoint;
import org.apache.camel.component.dapr.StateOperation;
import org.apache.camel.util.ObjectHelper;

public class DaprStateHandler implements DaprOperationHandler {

    private final DaprConfigurationOptionsProxy configurationOptionsProxy;
    private final DaprEndpoint endpoint;

    public DaprStateHandler(DaprConfigurationOptionsProxy configurationOptionsProxy, DaprEndpoint endpoint) {
        this.configurationOptionsProxy = configurationOptionsProxy;
        this.endpoint = endpoint;
    }

    @Override
    public DaprOperationResponse handle(Exchange exchange) {
        StateOperation stateOperation = configurationOptionsProxy.getStateOperation(exchange);
        DaprClient client = endpoint.getClient();

        switch (stateOperation) {
            case save:
                return saveState(exchange, client);
            case saveBulk:
                return saveBulkState(exchange, client);
            case get:
                return getState(exchange, client);
            case getBulk:
                return getBulkState(exchange, client);
            case delete:
                return deleteState(exchange, client);
            case executeTransaction:
                return executeStateTransaction(exchange, client);
            default:
                throw new IllegalArgumentException("Unsupported state operation");
        }
    }

    private DaprOperationResponse saveState(Exchange exchange, DaprClient client) {
        Object payload = exchange.getIn().getBody();
        String stateStore = configurationOptionsProxy.getStateStore(exchange);
        String key = configurationOptionsProxy.getKey(exchange);
        String eTag = configurationOptionsProxy.getETag(exchange);
        StateOptions stateOptions = getStateOptions(exchange);

        client.saveState(stateStore, key, eTag, payload, stateOptions).block();

        return DaprOperationResponse.create(new State<>(key, payload, eTag, stateOptions));
    }

    private DaprOperationResponse saveBulkState(Exchange exchange, DaprClient client) {
        String stateStore = configurationOptionsProxy.getStateStore(exchange);
        List<State<?>> states = configurationOptionsProxy.getStates(exchange);

        SaveStateRequest stateRequest = new SaveStateRequest(stateStore);
        stateRequest.setStates(states);

        client.saveBulkState(stateRequest);

        return DaprOperationResponse.create(stateRequest);
    }

    private DaprOperationResponse getState(Exchange exchange, DaprClient client) {
        String stateStore = configurationOptionsProxy.getStateStore(exchange);
        String key = configurationOptionsProxy.getKey(exchange);
        Map<String, String> metadata = configurationOptionsProxy.getMetadata(exchange);
        StateOptions stateOptions = getStateOptions(exchange);

        GetStateRequest stateRequest = new GetStateRequest(stateStore, key);
        stateRequest.setMetadata(metadata);
        stateRequest.setStateOptions(stateOptions);

        State<byte[]> response = client.getState(stateRequest, TypeRef.get(byte[].class)).block();

        return DaprOperationResponse.create(response);
    }

    private DaprOperationResponse getBulkState(Exchange exchange, DaprClient client) {
        String stateStore = configurationOptionsProxy.getStateStore(exchange);
        List<String> keys = configurationOptionsProxy.getKeys(exchange);
        Map<String, String> metadata = configurationOptionsProxy.getMetadata(exchange);

        GetBulkStateRequest stateRequest = new GetBulkStateRequest(stateStore, keys);
        stateRequest.setMetadata(metadata);

        List<State<byte[]>> response = client.getBulkState(stateRequest, TypeRef.get(byte[].class)).block();

        return DaprOperationResponse.create(response);
    }

    private DaprOperationResponse deleteState(Exchange exchange, DaprClient client) {
        String stateStore = configurationOptionsProxy.getStateStore(exchange);
        String key = configurationOptionsProxy.getKey(exchange);
        String eTag = configurationOptionsProxy.getETag(exchange);
        Map<String, String> metadata = configurationOptionsProxy.getMetadata(exchange);
        StateOptions stateOptions = getStateOptions(exchange);

        DeleteStateRequest stateRequest = new DeleteStateRequest(stateStore, key);
        stateRequest.setEtag(eTag);
        stateRequest.setMetadata(metadata);
        stateRequest.setStateOptions(stateOptions);

        client.deleteState(stateRequest).block();

        return DaprOperationResponse.create(stateRequest);
    }

    private DaprOperationResponse executeStateTransaction(Exchange exchange, DaprClient client) {
        String stateStore = configurationOptionsProxy.getStateStore(exchange);
        List<TransactionalStateOperation<?>> transactions = configurationOptionsProxy.getTransactions(exchange);
        Map<String, String> metadata = configurationOptionsProxy.getMetadata(exchange);

        ExecuteStateTransactionRequest stateRequest = new ExecuteStateTransactionRequest(stateStore);
        stateRequest.setOperations(transactions);
        stateRequest.setMetadata(metadata);

        client.executeStateTransaction(stateRequest);

        return DaprOperationResponse.create(stateRequest);
    }

    private StateOptions getStateOptions(Exchange exchange) {
        StateOptions.Concurrency concurrency = configurationOptionsProxy.getConcurrency(exchange);
        StateOptions.Consistency consistency = configurationOptionsProxy.getConsistency(exchange);

        StateOptions stateOptions = null;
        if (concurrency != null || consistency != null) {
            stateOptions = new StateOptions(consistency, concurrency);
        }

        return stateOptions;
    }

    @Override
    public void validateConfiguration(Exchange exchange) {
        String stateStore = configurationOptionsProxy.getStateStore(exchange);
        String key = configurationOptionsProxy.getKey(exchange);
        StateOperation stateOperation = configurationOptionsProxy.getStateOperation(exchange);

        if (ObjectHelper.isEmpty(stateStore)) {
            throw new IllegalArgumentException("State store not configured");
        }

        switch (stateOperation) {
            case save:
                Object payload = exchange.getIn().getBody();
                if (ObjectHelper.isEmpty(key) || ObjectHelper.isEmpty(payload)) {
                    throw new IllegalArgumentException("Key and payload must not be empty for 'save' operation");
                }
                break;
            case saveBulk:
                List<State<?>> states = configurationOptionsProxy.getStates(exchange);
                if (ObjectHelper.isEmpty(states)) {
                    throw new IllegalArgumentException("States must not be empty for 'saveBulk' operation");
                }
                break;
            case get:
            case delete:
                if (ObjectHelper.isEmpty(key)) {
                    throw new IllegalArgumentException("Key must not be empty for 'get' and 'delete' operations");
                }
                break;
            case getBulk:
                List<String> keys = configurationOptionsProxy.getKeys(exchange);
                if (ObjectHelper.isEmpty(keys)) {
                    throw new IllegalArgumentException("Keys must not be empty for 'getBulk' operation");
                }
                break;
            case executeTransaction:
                List<TransactionalStateOperation<?>> transactions = configurationOptionsProxy.getTransactions(exchange);
                if (ObjectHelper.isEmpty(transactions)) {
                    throw new IllegalArgumentException("Transactions must not be empty for 'executeTransaction' operation");
                }
                break;
            default:
                throw new IllegalArgumentException("Unsupported state operation");
        }
    }
}
