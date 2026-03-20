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

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

/**
 * Perform key-value operations against a pluggable state store backend.
 */
@UriEndpoint(firstVersion = "4.19.0", scheme = "state-store", title = "State Store",
             syntax = "state-store:storeName", producerOnly = true,
             remote = false, category = { Category.CACHE },
             headersClass = StateStoreConstants.class)
public class StateStoreEndpoint extends DefaultEndpoint {

    @UriPath(description = "The name of the state store")
    @Metadata(required = true)
    private String storeName;

    @UriParam(description = "The default operation to perform", enums = "put,putIfAbsent,get,delete,contains,keys,size,clear")
    private StateStoreOperations operation;

    @UriParam(description = "The backend to use. Default is an in-memory store. Set to a bean reference (e.g. #myBackend) for custom backends.",
              defaultValue = "memory", label = "advanced")
    private StateStoreBackend backend;

    @UriParam(description = "Time-to-live in milliseconds for entries. 0 means no expiry.", defaultValue = "0")
    private long ttl;

    public StateStoreEndpoint(String endpointUri, StateStoreComponent component) {
        super(endpointUri, component);
    }

    @Override
    public Producer createProducer() {
        return new StateStoreProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) {
        throw new UnsupportedOperationException("The state-store component does not support consumers");
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        StateStoreComponent comp = (StateStoreComponent) getComponent();
        backend = comp.getOrCreateBackend(storeName, backend);
        backend.start();
    }

    public String getStoreName() {
        return storeName;
    }

    public void setStoreName(String storeName) {
        this.storeName = storeName;
    }

    public StateStoreOperations getOperation() {
        return operation;
    }

    public void setOperation(StateStoreOperations operation) {
        this.operation = operation;
    }

    public StateStoreBackend getBackend() {
        return backend;
    }

    public void setBackend(StateStoreBackend backend) {
        this.backend = backend;
    }

    public long getTtl() {
        return ttl;
    }

    public void setTtl(long ttl) {
        this.ttl = ttl;
    }
}
