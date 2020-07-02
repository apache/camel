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
package org.apache.camel.component.vertx.websocket;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.ext.web.Router;
import org.apache.camel.Endpoint;
import org.apache.camel.SSLContextParametersAware;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.vertx.websocket.VertxWebsocketHelper.createHostKey;
import static org.apache.camel.component.vertx.websocket.VertxWebsocketHelper.extractHostName;
import static org.apache.camel.component.vertx.websocket.VertxWebsocketHelper.extractPath;
import static org.apache.camel.component.vertx.websocket.VertxWebsocketHelper.extractPortNumber;

@Component("vertx-websocket")
public class VertxWebsocketComponent extends DefaultComponent implements SSLContextParametersAware {

    private static final Logger LOG = LoggerFactory.getLogger(VertxWebsocketComponent.class);

    private final Map<VertxWebsocketHostKey, VertxWebsocketHost> vertxHostRegistry = new ConcurrentHashMap<>();

    @Metadata(label = "advanced")
    private Vertx vertx;
    @Metadata(label = "advanced")
    private VertxOptions vertxOptions;
    @Metadata(label = "security", defaultValue = "false")
    private boolean useGlobalSslContextParameters;

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        VertxWebsocketConfiguration configuration = new VertxWebsocketConfiguration();
        configuration.setHost(extractHostName(remaining));
        configuration.setPort(extractPortNumber(remaining));
        configuration.setPath(extractPath(remaining));

        VertxWebsocketEndpoint endpoint = new VertxWebsocketEndpoint(uri, this, configuration);
        setProperties(endpoint, parameters);

        if (configuration.getSslContextParameters() == null) {
            configuration.setSslContextParameters(retrieveGlobalSslContextParameters());
        }

        return endpoint;
    }

    public void connectConsumer(VertxWebsocketConsumer consumer) {
        VertxWebsocketEndpoint endpoint = consumer.getEndpoint();
        VertxWebsocketConfiguration configuration = endpoint.getConfiguration();
        VertxWebsocketHostKey hostKey = createHostKey(configuration);
        VertxWebsocketHost host = vertxHostRegistry.computeIfAbsent(hostKey, key -> {
            Router router = configuration.getRouter();
            if (router == null) {
                Set<Router> routers = getCamelContext().getRegistry().findByType(Router.class);
                if (routers.size() == 1) {
                    router = routers.iterator().next();
                }

                if (router == null) {
                    router = Router.router(getVertx());
                }
            }

            VertxWebsocketHostConfiguration hostConfiguration = new VertxWebsocketHostConfiguration(
                    getVertx(),
                    router,
                    configuration.getServerOptions(),
                    configuration.getSslContextParameters());

            return createVertxWebsocketHost(hostConfiguration, hostKey);
        });

        host.connect(consumer);
        try {
            host.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void disconnectConsumer(VertxWebsocketConsumer consumer) {
        VertxWebsocketEndpoint endpoint = consumer.getEndpoint();
        VertxWebsocketConfiguration configuration = endpoint.getConfiguration();
        VertxWebsocketHostKey hostKey = createHostKey(configuration);
        VertxWebsocketHost vertxWebsocketHost = vertxHostRegistry.remove(hostKey);

        if (vertxWebsocketHost != null) {
            vertxWebsocketHost.disconnect(configuration.getPath());
        }
    }

    public Vertx getVertx() {
        if (vertx == null) {
            Set<Vertx> vertxes = getCamelContext().getRegistry().findByType(Vertx.class);
            if (vertxes.size() == 1) {
                vertx  = vertxes.iterator().next();
            }
        }

        if (vertx == null) {
            if (vertxOptions != null) {
                vertx = Vertx.vertx(vertxOptions);
            } else {
                vertx = Vertx.vertx();
            }
        }

        return vertx;
    }

    /**
     * To use an existing vertx instead of creating a new instance
     */
    public void setVertx(Vertx vertx) {
        this.vertx = vertx;
    }

    public VertxOptions getVertxOptions() {
        return vertxOptions;
    }

    /**
     * To provide a custom set of vertx options for configuring vertx
     */
    public void setVertxOptions(VertxOptions vertxOptions) {
        this.vertxOptions = vertxOptions;
    }

    @Override
    public boolean isUseGlobalSslContextParameters() {
        return this.useGlobalSslContextParameters;
    }

    /**
     * Enable usage of global SSL context parameters.
     */
    @Override
    public void setUseGlobalSslContextParameters(boolean useGlobalSslContextParameters) {
        this.useGlobalSslContextParameters = useGlobalSslContextParameters;
    }

    protected Map<VertxWebsocketHostKey, VertxWebsocketHost> getVerxHostRegistry() {
        return this.vertxHostRegistry;
    }

    protected VertxWebsocketHost createVertxWebsocketHost(VertxWebsocketHostConfiguration hostConfiguration, VertxWebsocketHostKey hostKey) {
        return new VertxWebsocketHost(hostConfiguration, hostKey);
    }
}
