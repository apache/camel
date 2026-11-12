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
package org.apache.camel.component.vertx;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.VertxBuilder;
import io.vertx.core.VertxOptions;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component("vertx")
public class VertxComponent extends DefaultComponent {

    private static final Logger LOG = LoggerFactory.getLogger(VertxComponent.class);

    private volatile boolean createdVertx;

    @Metadata(label = "advanced")
    private VertxBuilder vertxFactory;
    @Metadata(autowired = true)
    private Vertx vertx;
    @Metadata
    private String host;
    @Metadata
    private int port;
    @Metadata(defaultValue = "60")
    private int timeout = 60;
    @Metadata
    private VertxOptions vertxOptions;

    public VertxComponent() {
    }

    public VertxComponent(CamelContext context) {
        super(context);
    }

    public VertxBuilder getVertxFactory() {
        return vertxFactory;
    }

    /**
     * To use a custom VertxFactory implementation
     */
    public void setVertxFactory(VertxBuilder vertxFactory) {
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

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        VertxEndpoint endpoint = new VertxEndpoint(uri, this, remaining);
        setProperties(endpoint, parameters);
        return endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (vertx == null) {

            boolean clustered = false;

            if (vertxOptions == null) {
                vertxOptions = new VertxOptions();
                if (ObjectHelper.isNotEmpty(host)) {
                    vertxOptions.getEventBusOptions().setHost(host);
                    clustered = true;
                }
                if (port > 0) {
                    vertxOptions.getEventBusOptions().setPort(port);
                    clustered = true;
                }
            }

            if (vertxFactory == null) {
                vertxFactory = Vertx.builder().with(vertxOptions);
            }

            // we are creating vertx so we should handle its lifecycle
            createdVertx = true;

            final CountDownLatch latch = new CountDownLatch(1);
            final AtomicReference<Throwable> failure = new AtomicReference<>();
            // the callback writes here rather than to the field, so a build that completes after the timeout
            // cannot leave a half-published Vert.x behind
            final AtomicReference<Vertx> created = new AtomicReference<>();
            Future<Vertx> clusteredVertx = null;

            // lets using a host / port if a host name is specified
            if (clustered) {
                if (timeout <= 0) {
                    throw new IllegalArgumentException(
                            "The timeout option must be a positive number of seconds, was: " + timeout);
                }

                LOG.info("Creating Clustered Vertx {}:{}", vertxOptions.getEventBusOptions().getHost(),
                        vertxOptions.getEventBusOptions().getPort());
                // use the async api as we want to wait for the eventbus to be ready before we are in started state
                clusteredVertx = vertxFactory.buildClustered();
                clusteredVertx.onComplete(event -> {
                    if (event.cause() != null) {
                        failure.set(event.cause());
                    } else if (event.succeeded()) {
                        created.set(event.result());
                    }

                    latch.countDown();
                });
            } else {
                LOG.info("Creating Non-Clustered Vertx");
                created.set(vertxFactory.build());
                latch.countDown();
            }

            if (latch.getCount() > 0) {
                LOG.info("Waiting for EventBus to be ready using {} sec as timeout", timeout);
                if (!latch.await(timeout, TimeUnit.SECONDS)) {
                    // the build is still running: a node that joins the cluster after we gave up would keep its
                    // event-loop threads alive with nobody left to close it. onSuccess also fires at once if the
                    // future completed between the await and this registration, so no window is left open
                    clusteredVertx.onSuccess(v -> {
                        LOG.warn("Closing the Vert.x that became ready after the timeout of {} seconds,"
                                 + " as the component start has already failed",
                                timeout);
                        v.close();
                    });
                    throw new IllegalStateException(
                            "The Vert.x EventBus was not ready within the timeout of " + timeout + " seconds");
                }
            }

            // the component must not reach the started state with a half-built Vert.x: every endpoint created
            // afterwards would dereference it
            if (failure.get() != null) {
                throw new IllegalStateException(
                        "Error creating Clustered Vertx " + host + ":" + port, failure.get());
            }
            if (created.get() == null) {
                throw new IllegalStateException("The Vert.x instance was not created");
            }

            vertx = created.get();
            LOG.info("EventBus is ready: {}", vertx);
        } else {
            LOG.debug("Using Vertx instance set on the component level.");
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
