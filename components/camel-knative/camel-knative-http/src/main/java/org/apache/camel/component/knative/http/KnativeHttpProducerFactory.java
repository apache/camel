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
package org.apache.camel.component.knative.http;

import java.util.Objects;

import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClientOptions;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Endpoint;
import org.apache.camel.Producer;
import org.apache.camel.component.knative.spi.KnativeProducerFactory;
import org.apache.camel.component.knative.spi.KnativeResource;
import org.apache.camel.component.knative.spi.KnativeTransportConfiguration;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;

public class KnativeHttpProducerFactory extends ServiceSupport implements CamelContextAware, KnativeProducerFactory {
    private Vertx vertx;
    private WebClientOptions vertxHttpClientOptions;
    private CamelContext camelContext;

    public KnativeHttpProducerFactory() {
    }

    public KnativeHttpProducerFactory(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public Vertx getVertx() {
        return vertx;
    }

    public KnativeHttpProducerFactory setVertx(Vertx vertx) {
        if (ServiceHelper.isStarted(this)) {
            throw new IllegalArgumentException("Can't set the Vertx instance after the service has been started");
        }

        this.vertx = vertx;
        return this;
    }

    public WebClientOptions getClientOptions() {
        return vertxHttpClientOptions;
    }

    public void setClientOptions(WebClientOptions vertxHttpClientOptions) {
        this.vertxHttpClientOptions = vertxHttpClientOptions;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    protected void doInit() throws Exception {
        if (vertx == null) {
            vertx = KnativeHttpSupport.lookupVertxInstance(camelContext);
        }
    }

    @Override
    public Producer createProducer(Endpoint endpoint, KnativeTransportConfiguration config, KnativeResource service) {
        Objects.requireNonNull(this.vertx, "vertx");

        return new KnativeHttpProducer(
                endpoint,
                service,
                this.vertx,
                this.vertxHttpClientOptions);
    }
}
