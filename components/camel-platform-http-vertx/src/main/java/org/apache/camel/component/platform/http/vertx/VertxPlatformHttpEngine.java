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
package org.apache.camel.component.platform.http.vertx;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.component.platform.http.PlatformHttpConstants;
import org.apache.camel.component.platform.http.PlatformHttpEndpoint;
import org.apache.camel.component.platform.http.spi.PlatformHttpEngine;
import org.apache.camel.spi.annotations.JdkService;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.service.ServiceSupport;

/**
 * Implementation of the {@link PlatformHttpEngine} based on Vert.x Web.
 */
@JdkService(PlatformHttpConstants.PLATFORM_HTTP_ENGINE_FACTORY)
public class VertxPlatformHttpEngine extends ServiceSupport implements PlatformHttpEngine, CamelContextAware {

    private CamelContext camelContext;
    private List<Handler<RoutingContext>> handlers;
    private int port;

    public VertxPlatformHttpEngine() {
        this.handlers = Collections.emptyList();
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public List<Handler<RoutingContext>> getHandlers() {
        return Collections.unmodifiableList(handlers);
    }

    public void setHandlers(List<Handler<RoutingContext>> handlers) {
        if (handlers != null) {
            this.handlers = new ArrayList<>(handlers);
        }
    }

    @Override
    protected void doStart() throws Exception {
        // no-op
    }

    @Override
    protected void doStop() throws Exception {
        // no-op
    }

    @Override
    public Consumer createConsumer(PlatformHttpEndpoint endpoint, Processor processor) {
        return new VertxPlatformHttpConsumer(
                endpoint,
                processor,
                handlers);
    }

    @Override
    public int getServerPort() {
        if (port == 0) {
            VertxPlatformHttpServer server = CamelContextHelper.findSingleByType(camelContext, VertxPlatformHttpServer.class);
            if (server != null && server.getServer() != null) {
                port = server.getServer().actualPort();
            }
            if (port == 0) {
                VertxPlatformHttpServerConfiguration config
                        = CamelContextHelper.findSingleByType(camelContext, VertxPlatformHttpServerConfiguration.class);
                if (config != null) {
                    port = config.getBindPort();
                }
            }
            if (port == 0) {
                VertxPlatformHttpRouter router = VertxPlatformHttpRouter.lookup(camelContext);
                if (router != null && router.getServer() != null && router.getServer().getServer() != null) {
                    port = router.getServer().getServer().actualPort();
                }
            }
        }
        return port;
    }
}
