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
package org.apache.camel.component.statestore;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;

/**
 * Producer for the State Store component that performs key-value operations.
 */
public class StateStoreProducer extends DefaultProducer {

    private final StateStoreEndpoint endpoint;

    public StateStoreProducer(StateStoreEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        StateStoreOperations op = determineOperation(exchange);
        if (op == null) {
            throw new IllegalArgumentException(
                    "No operation specified. Set the operation via URI option or header " + StateStoreConstants.OPERATION);
        }

        StateStoreBackend backend = endpoint.getBackend();
        long ttl = determineTtl(exchange);
        Message message = exchange.getMessage();

        switch (op) {
            case put -> {
                String key = requireKey(message);
                Object value = message.getBody();
                Object previous = backend.put(key, value, ttl);
                message.setBody(previous);
            }
            case putIfAbsent -> {
                String key = requireKey(message);
                Object value = message.getBody();
                Object existing = backend.putIfAbsent(key, value, ttl);
                message.setBody(existing);
            }
            case get -> {
                String key = requireKey(message);
                Object value = backend.get(key);
                message.setBody(value);
            }
            case delete -> {
                String key = requireKey(message);
                Object removed = backend.delete(key);
                message.setBody(removed);
            }
            case contains -> {
                String key = requireKey(message);
                boolean exists = backend.contains(key);
                message.setBody(exists);
            }
            case keys -> {
                message.setBody(backend.keys());
            }
            case size -> {
                message.setBody(backend.size());
            }
            case clear -> {
                backend.clear();
                message.setBody(null);
            }
            default -> throw new IllegalArgumentException("Unsupported operation: " + op);
        }
    }

    private StateStoreOperations determineOperation(Exchange exchange) {
        Object headerOp = exchange.getMessage().getHeader(StateStoreConstants.OPERATION);
        if (headerOp != null) {
            if (headerOp instanceof StateStoreOperations sso) {
                return sso;
            }
            return StateStoreOperations.valueOf(headerOp.toString());
        }
        return endpoint.getOperation();
    }

    private long determineTtl(Exchange exchange) {
        Long headerTtl = exchange.getMessage().getHeader(StateStoreConstants.TTL, Long.class);
        if (headerTtl != null) {
            return headerTtl;
        }
        return endpoint.getTtl();
    }

    private String requireKey(Message message) {
        String key = message.getHeader(StateStoreConstants.KEY, String.class);
        if (key == null) {
            throw new IllegalArgumentException("Header " + StateStoreConstants.KEY + " is required for this operation");
        }
        return key;
    }
}
