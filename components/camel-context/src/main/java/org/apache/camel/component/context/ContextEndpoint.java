/**
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
package org.apache.camel.component.context;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.DelegateEndpoint;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriPath;

@UriEndpoint(scheme = "context", title = "Camel Context", syntax = "context:contextId:localEndpointUrl", label = "endpoint")
public class ContextEndpoint extends DefaultEndpoint implements DelegateEndpoint {

    @UriPath @Metadata(required = "true")
    private String contextId;
    @UriPath @Metadata(required = "true")
    private String localEndpointUrl;

    private final Endpoint delegate;

    public ContextEndpoint(String endpointUri, Component component, Endpoint delegate) {
        super(endpointUri, component);
        this.delegate = delegate;
    }

    @Override
    public Endpoint getEndpoint() {
        return delegate;
    }

    public String getContextId() {
        return contextId;
    }

    /**
     * Is the ID you used to register the CamelContext into the Registry.
     */
    public void setContextId(String contextId) {
        this.contextId = contextId;
    }

    public String getLocalEndpointUrl() {
        return localEndpointUrl;
    }

    /**
     * Can be a valid Camel URI evaluated within the black box CamelContext.
     * Or it can be a logical name which is mapped to any local endpoints.
     * For example if you locally have endpoints like direct:invoices and seda:purchaseOrders
     * inside a CamelContext of id supplyChain, then you can just use the URIs supplyChain:invoices
     * or supplyChain:purchaseOrders to omit the physical endpoint kind and use pure logical URIs.
     */
    public void setLocalEndpointUrl(String localEndpointUrl) {
        this.localEndpointUrl = localEndpointUrl;
    }

    @Override
    public Producer createProducer() throws Exception {
        return delegate.createProducer();
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        return delegate.createConsumer(processor);
    }

    @Override
    public boolean isSingleton() {
        return delegate.isSingleton();
    }
}
