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
package org.apache.camel.component.vertx;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.impl.VertxFactoryImpl;
import io.vertx.core.spi.VertxFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.ComponentConfiguration;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.spi.EndpointCompleter;
import org.apache.camel.spi.Metadata;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Camel Component for <a href="http://vertx.io/">vert.x</a>
 */
public class VertxComponent extends UriEndpointComponent implements EndpointCompleter {
    private static final Logger LOG = LoggerFactory.getLogger(VertxComponent.class);

    private volatile boolean createdVertx;

    @Metadata(label = "advanced")
    private VertxFactory vertxFactory;
    private Vertx vertx;
    private String host;
    private int port;
    @Metadata(defaultValue = "60")
    private int timeout = 60;
    private VertxOptions vertxOptions;

    public VertxComponent() {
        super(VertxEndpoint.class);
    }

    public VertxComponent(CamelContext context) {
        super(context, VertxEndpoint.class);
    }

    public VertxFactory getVertxFactory() {
        return vertxFactory;
    }

    /**
     * To use a custom VertxFactory implementation
     */
    public void setVertxFactory(VertxFactory vertxFactory) {
        this.vertxFactory = vertxFactory;
    }

    public String getHost() {
        return host;
    }

    /**
     * Hostname for creating an embedded clustered EventBus
     */
    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    /**
     * Port for creating an embedded clustered EventBus
     */
    public void setPort(int port) {
        this.port = port;
    }

    public VertxOptions getVertxOptions() {
        return vertxOptions;
    }

    /**
     * Options to use for creating vertx
     */
    public void setVertxOptions(VertxOptions vertxOptions) {
        this.vertxOptions = vertxOptions;
    }

    public Vertx getVertx() {
        return vertx;
    }

    /**
     * To use the given vertx EventBus instead of creating a new embedded EventBus
     */
    public void setVertx(Vertx vertx) {
        this.vertx = vertx;
    }

    public int getTimeout() {
        return timeout;
    }

    /**
     * Timeout in seconds to wait for clustered Vertx EventBus to be ready.
     * <p/>
     * The default value is 60.
     */
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        VertxEndpoint endpoint = new VertxEndpoint(uri, this, remaining);
        setProperties(endpoint, parameters);
        return endpoint;
    }

    public List<String> completeEndpointPath(ComponentConfiguration componentConfiguration, String text) {
        // TODO is there any way to find out the list of endpoint names in vertx?
        return null;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (vertx == null) {

            if (vertxFactory == null) {
                vertxFactory = new VertxFactoryImpl();
            }

            if (vertxOptions == null) {
                vertxOptions = new VertxOptions();
                if (ObjectHelper.isNotEmpty(host)) {
                    vertxOptions.setClusterHost(host);
                    vertxOptions.setClustered(true);
                }
                if (port > 0) {
                    vertxOptions.setClusterPort(port);
                    vertxOptions.setClustered(true);
                }
            }

            // we are creating vertx so we should handle its lifecycle
            createdVertx = true;

            final CountDownLatch latch = new CountDownLatch(1);

            // lets using a host / port if a host name is specified
            if (vertxOptions.isClustered()) {
                LOG.info("Creating Clustered Vertx {}:{}", vertxOptions.getClusterHost(), vertxOptions.getClusterPort());
                // use the async api as we want to wait for the eventbus to be ready before we are in started state
                vertxFactory.clusteredVertx(vertxOptions, new Handler<AsyncResult<Vertx>>() {
                    @Override
                    public void handle(AsyncResult<Vertx> event) {
                        if (event.cause() != null) {
                            LOG.warn("Error creating Clustered Vertx " + host + ":" + port + " due " + event.cause().getMessage(), event.cause());
                        } else if (event.succeeded()) {
                            vertx = event.result();
                            LOG.info("EventBus is ready: {}", vertx);
                        }

                        latch.countDown();
                    }
                });
            } else {
                LOG.info("Creating Non-Clustered Vertx");
                vertx = vertxFactory.vertx();
                LOG.info("EventBus is ready: {}", vertx);
                latch.countDown();
            }

            if (latch.getCount() > 0) {
                LOG.info("Waiting for EventBus to be ready using {} sec as timeout", timeout);
                latch.await(timeout, TimeUnit.SECONDS);
            }
        } else {
            LOG.debug("Using Vert.x instance set on the component level.");
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        if (createdVertx && vertx != null) {
            LOG.info("Stopping Vertx {}", vertx);
            vertx.close();
        }
    }
}
