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

import io.swagger.v3.oas.models.OpenAPI;
import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProducer;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.spi.ProducerCache;
import org.apache.camel.support.cache.DefaultProducerCache;
import org.apache.camel.support.processor.DelegateAsyncProcessor;
import org.apache.camel.support.service.ServiceHelper;

public class RestOpenApiProcessor extends DelegateAsyncProcessor implements CamelContextAware {

    private CamelContext camelContext;
    private final OpenAPI openAPI;
    private final String basePath;
    private ProducerCache producerCache;

    public RestOpenApiProcessor(OpenAPI openAPI, String basePath, Processor processor) {
        super(processor);
        this.basePath = basePath;
        this.openAPI = openAPI;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        String path = exchange.getMessage().getHeader(Exchange.HTTP_PATH, String.class);
        if (path != null && path.startsWith(basePath)) {
            path = path.substring(basePath.length() + 1);
        }

        // TODO: choose processor strategy (mapping by operation id -> direct)
        // TODO: check if valid operation according to OpenApi
        // TODO: validate GET/POST etc
        // TODO: 404 and so on
        // TODO: binding

        Endpoint e = camelContext.getEndpoint("direct:" + path);
        AsyncProducer p = producerCache.acquireProducer(e);
        return p.process(exchange, new AsyncCallback() {
            @Override
            public void done(boolean doneSync) {
                producerCache.releaseProducer(e, p);
                callback.done(doneSync);
            }
        });
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();
        producerCache = new DefaultProducerCache(this, getCamelContext(), 1000);
        ServiceHelper.initService(producerCache);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        ServiceHelper.startService(producerCache);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        ServiceHelper.stopService(producerCache);
    }
}
