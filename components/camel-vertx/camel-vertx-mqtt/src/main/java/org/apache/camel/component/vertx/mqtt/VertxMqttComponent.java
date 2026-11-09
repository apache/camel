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
package org.apache.camel.component.vertx.mqtt;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import org.apache.camel.Endpoint;
import org.apache.camel.SSLContextParametersAware;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.DefaultComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(VertxMqttConstants.SCHEME)
public class VertxMqttComponent extends DefaultComponent implements SSLContextParametersAware {

    private static final Logger LOG = LoggerFactory.getLogger(VertxMqttComponent.class);

    /**
     * The shared configuration of the endpoints.
     */
    @Metadata
    private VertxMqttConfiguration configuration = new VertxMqttConfiguration();
    /**
     * The Vert.x instance to use. Without, the one of the registry if there is exactly one, else a Vert.x instance of
     * the component's own, closed when the component stops.
     */
    @Metadata(label = "advanced", autowired = true)
    private Vertx vertx;
    /**
     * The options of the Vert.x instance the component creates when none is given.
     */
    @Metadata(label = "advanced")
    private VertxOptions vertxOptions;
    /**
     * Whether to use the global SSL context parameters of the Camel context for an endpoint that sets none.
     */
    @Metadata(label = "security", defaultValue = "false")
    private boolean useGlobalSslContextParameters;

    private boolean managedVertx;

    @Override
    protected Endpoint createEndpoint(final String uri, final String remaining, final Map<String, Object> parameters)
            throws Exception {
        final VertxMqttConfiguration config = getConfiguration().copy();
        final VertxMqttEndpoint endpoint = new VertxMqttEndpoint(uri, this, config, remaining);
        setProperties(endpoint, parameters);
        if (config.getSslContextParameters() == null) {
            config.setSslContextParameters(retrieveGlobalSslContextParameters());
        }
        return endpoint;
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();
        if (vertx != null) {
            return;
        }
        final Vertx registered = CamelContextHelper.findSingleByType(getCamelContext(), Vertx.class);
        if (registered != null) {
            vertx = registered;
        } else {
            LOG.debug("Creating a Vert.x instance for the component");
            vertx = vertxOptions != null ? Vertx.vertx(vertxOptions) : Vertx.vertx();
            managedVertx = true;
        }
    }

    @Override
    protected void doShutdown() throws Exception {
        if (managedVertx && vertx != null) {
            final Vertx closing = vertx;
            vertx = null;
            managedVertx = false;
            try {
                closing.close().toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS);
            } catch (final Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                LOG.warn("Closing the Vert.x instance of the component did not complete", e);
            }
        }
        super.doShutdown();
    }

    public VertxMqttConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(final VertxMqttConfiguration configuration) {
        this.configuration = configuration;
    }

    public Vertx getVertx() {
        return vertx;
    }

    public void setVertx(final Vertx vertx) {
        this.vertx = vertx;
    }

    public VertxOptions getVertxOptions() {
        return vertxOptions;
    }

    public void setVertxOptions(final VertxOptions vertxOptions) {
        this.vertxOptions = vertxOptions;
    }

    @Override
    public boolean isUseGlobalSslContextParameters() {
        return useGlobalSslContextParameters;
    }

    @Override
    public void setUseGlobalSslContextParameters(final boolean useGlobalSslContextParameters) {
        this.useGlobalSslContextParameters = useGlobalSslContextParameters;
    }
}
