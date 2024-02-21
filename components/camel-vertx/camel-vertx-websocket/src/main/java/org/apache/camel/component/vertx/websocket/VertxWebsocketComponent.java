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

import java.net.URI;
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
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.UnsafeUriCharactersEncoder;

import static org.apache.camel.component.vertx.websocket.VertxWebsocketHelper.createHostKey;

@Component("vertx-websocket")
public class VertxWebsocketComponent extends DefaultComponent implements SSLContextParametersAware {

    private final Map<VertxWebsocketHostKey, VertxWebsocketHost> vertxHostRegistry = new ConcurrentHashMap<>();
    private boolean managedVertx;

    @Metadata(label = "advanced")
    private Vertx vertx;
    @Metadata(label = "advanced")
    private VertxOptions vertxOptions;
    @Metadata(label = "advanced")
    private Router router;
    @Metadata(label = "security", defaultValue = "false")
    private boolean useGlobalSslContextParameters;
    @Metadata(label = "advanced", defaultValue = VertxWebsocketConstants.DEFAULT_VERTX_SERVER_HOST)
    private String defaultHost = VertxWebsocketConstants.DEFAULT_VERTX_SERVER_HOST;
    @Metadata(label = "advanced", defaultValue = "" + VertxWebsocketConstants.DEFAULT_VERTX_SERVER_PORT)
    private int defaultPort = VertxWebsocketConstants.DEFAULT_VERTX_SERVER_PORT;
    @Metadata(label = "advanced", defaultValue = "true")
    private boolean allowOriginHeader = true;
    @Metadata(label = "advanced")
    private String originHeaderUrl;

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        String wsUri = remaining;
        if (wsUri.matches("^wss?:.*")) {
            int schemeSeparatorIndex = remaining.indexOf(":");
            String scheme = remaining.substring(0, schemeSeparatorIndex);
            wsUri = scheme + "://" + wsUri.replaceFirst("wss?:/*", "");
        } else {
            String scheme = "ws://";
            // Preserves backwards compatibility for the vertx-websocket  on camel-quarkus / camel-k where the HTTP
            // server is provided by the runtime platform and the host:port configuration is not strictly required
            if (remaining.startsWith("/")) {
                wsUri = scheme + "/" + remaining.replaceAll("^/+", "");
            } else {
                wsUri = scheme + remaining;
            }
        }

        URI endpointUri = new URI(UnsafeUriCharactersEncoder.encodeHttpURI(wsUri));
        URI websocketURI = URISupport.createRemainingURI(endpointUri, parameters);

        if (websocketURI.getHost() == null || websocketURI.getPort() == -1 || ObjectHelper.isEmpty(websocketURI.getPath())) {
            String path = websocketURI.getPath();
            String host = websocketURI.getHost();
            int port = websocketURI.getPort();

            if (websocketURI.getHost() == null) {
                host = getDefaultHost();
            }

            if (websocketURI.getPort() == -1) {
                port = getDefaultPort();
            }

            if (ObjectHelper.isEmpty(path)) {
                path = "/";
            }

            websocketURI = new URI(
                    websocketURI.getScheme(), websocketURI.getUserInfo(),
                    host, port, path, websocketURI.getQuery(),
                    websocketURI.getFragment());
        }

        VertxWebsocketConfiguration configuration = new VertxWebsocketConfiguration();
        configuration.setWebsocketURI(websocketURI);
        configuration.setAllowOriginHeader(isAllowOriginHeader());
        configuration.setOriginHeaderUrl(getOriginHeaderUrl());

        VertxWebsocketEndpoint endpoint = createEndpointInstance(uri, configuration);
        setProperties(endpoint, parameters);

        if (configuration.getSslContextParameters() == null) {
            configuration.setSslContextParameters(retrieveGlobalSslContextParameters());
        }

        return endpoint;
    }

    protected VertxWebsocketEndpoint createEndpointInstance(String uri, VertxWebsocketConfiguration configuration) {
        return new VertxWebsocketEndpoint(uri, this, configuration);
    }

    @Override
    protected void doInit() throws Exception {
        if (vertx == null) {
            Set<Vertx> vertxes = getCamelContext().getRegistry().findByType(Vertx.class);
            if (vertxes.size() == 1) {
                vertx = vertxes.iterator().next();
            }
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (vertx == null) {
            if (vertxOptions != null) {
                vertx = Vertx.vertx(vertxOptions);
            } else {
                vertx = Vertx.vertx();
            }
            managedVertx = true;
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        if (managedVertx && vertx != null) {
            vertx.close();
        }
        vertx = null;
    }

    public void connectConsumer(VertxWebsocketConsumer consumer) {
        VertxWebsocketEndpoint endpoint = consumer.getEndpoint();
        VertxWebsocketConfiguration configuration = endpoint.getConfiguration();
        VertxWebsocketHostKey hostKey = createHostKey(configuration.getWebsocketURI());
        VertxWebsocketHost host = vertxHostRegistry.computeIfAbsent(hostKey, key -> {
            Router vertxRouter = configuration.getRouter();
            if (vertxRouter == null) {
                vertxRouter = router;

                if (vertxRouter == null) {
                    Set<Router> routers = getCamelContext().getRegistry().findByType(Router.class);
                    if (routers.size() == 1) {
                        vertxRouter = routers.iterator().next();
                    }
                }

                if (vertxRouter == null) {
                    vertxRouter = Router.router(getVertx());
                }
            }

            VertxWebsocketHostConfiguration hostConfiguration = new VertxWebsocketHostConfiguration(
                    getVertx(),
                    vertxRouter,
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
        VertxWebsocketHostKey hostKey = createHostKey(configuration.getWebsocketURI());
        VertxWebsocketHost vertxWebsocketHost = vertxHostRegistry.remove(hostKey);

        if (vertxWebsocketHost != null) {
            vertxWebsocketHost.disconnect(configuration.getWebsocketURI().getPath());
        }
    }

    public Vertx getVertx() {
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

    public Router getRouter() {
        return router;
    }

    /**
     * To provide a custom vertx router to use on the WebSocket server
     */
    public void setRouter(Router router) {
        this.router = router;
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

    protected Map<VertxWebsocketHostKey, VertxWebsocketHost> getVertxHostRegistry() {
        return this.vertxHostRegistry;
    }

    public boolean isAllowOriginHeader() {
        return allowOriginHeader;
    }

    /**
     * Whether the WebSocket client should add the Origin header to the WebSocket handshake request.
     */
    public void setAllowOriginHeader(boolean allowOriginHeader) {
        this.allowOriginHeader = allowOriginHeader;
    }

    public String getOriginHeaderUrl() {
        return originHeaderUrl;
    }

    /**
     * The value of the Origin header that the WebSocket client should use on the WebSocket handshake request. When not
     * specified, the WebSocket client will automatically determine the value for the Origin from the request URL.
     */
    public void setOriginHeaderUrl(String originHeaderUrl) {
        this.originHeaderUrl = originHeaderUrl;
    }

    protected VertxWebsocketHost createVertxWebsocketHost(
            VertxWebsocketHostConfiguration hostConfiguration, VertxWebsocketHostKey hostKey) {
        return new VertxWebsocketHost(getCamelContext(), hostConfiguration, hostKey);
    }

    /**
     * Default value for host name that the WebSocket should bind to
     */
    public void setDefaultHost(String defaultHost) {
        this.defaultHost = defaultHost;
    }

    public String getDefaultHost() {
        return this.defaultHost;
    }

    /**
     * Default value for the port that the WebSocket should bind to
     */
    public void setDefaultPort(int defaultPort) {
        this.defaultPort = defaultPort;
    }

    public int getDefaultPort() {
        return this.defaultPort;
    }
}
