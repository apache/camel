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

import io.vertx.ext.web.Router;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.component.knative.spi.KnativeConsumerFactory;
import org.apache.camel.component.knative.spi.KnativeResource;
import org.apache.camel.component.knative.spi.KnativeTransportConfiguration;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;

public class KnativeHttpConsumerFactory extends ServiceSupport implements CamelContextAware, KnativeConsumerFactory {
    private Router router;
    private CamelContext camelContext;

    public KnativeHttpConsumerFactory() {
    }

    public KnativeHttpConsumerFactory(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public Router getRouter() {
        return router;
    }

    public KnativeHttpConsumerFactory setRouter(Router router) {
        if (ServiceHelper.isStarted(this)) {
            throw new IllegalArgumentException("Can't set the Router instance after the service has been started");
        }

        this.router = router;
        return this;
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
    public Consumer createConsumer(
            Endpoint endpoint, KnativeTransportConfiguration config, KnativeResource service, Processor processor) {
        return new KnativeHttpConsumer(
                config,
                endpoint,
                service,
                this::lookupRouter,
                processor);
    }

    /**
     * Resolve router from given Camel context if not explicitly set. KnativeHttpConsumer implementation usually calls
     * this method to retrieve the router during service startup phase.
     */
    private Router lookupRouter() {
        if (router == null) {
            router = KnativeHttpSupport.lookupRouter(camelContext);
        }

        Objects.requireNonNull(router, "router");

        return router;
    }

}
