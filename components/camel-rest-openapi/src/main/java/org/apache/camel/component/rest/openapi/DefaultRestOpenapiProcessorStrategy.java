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
package org.apache.camel.component.rest.openapi;

import io.swagger.v3.oas.models.Operation;
import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProducer;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.NonManagedService;
import org.apache.camel.spi.ProducerCache;
import org.apache.camel.support.cache.DefaultProducerCache;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;

/**
 * Default {@link RestOpenapiProcessorStrategy} that links the Rest DSL to routes called via direct:operationId.
 */
public class DefaultRestOpenapiProcessorStrategy extends ServiceSupport
        implements RestOpenapiProcessorStrategy, CamelContextAware, NonManagedService {

    private CamelContext camelContext;
    private ProducerCache producerCache;
    private String component = "direct";

    @Override
    public boolean process(Operation operation, String path, Exchange exchange, AsyncCallback callback) {
        Endpoint e = camelContext.getEndpoint(component + ":" + operation.getOperationId());
        AsyncProducer p = producerCache.acquireProducer(e);
        return p.process(exchange, doneSync -> {
            try {
                producerCache.releaseProducer(e, p);
            } finally {
                callback.done(doneSync);
            }
        });
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public String getComponent() {
        return component;
    }

    /**
     * Name of component to use for processing the Rest DSL requests.
     */
    public void setComponent(String component) {
        this.component = component;
    }

    @Override
    protected void doInit() throws Exception {
        producerCache = new DefaultProducerCache(this, getCamelContext(), 1000);
        ServiceHelper.initService(producerCache);
    }

    @Override
    protected void doStart() throws Exception {
        ServiceHelper.startService(producerCache);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(producerCache);
    }

}
