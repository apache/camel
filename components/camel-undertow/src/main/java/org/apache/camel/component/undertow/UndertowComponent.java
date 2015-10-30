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
package org.apache.camel.component.undertow;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.handlers.PathHandler;
import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.component.undertow.handlers.HttpCamelHandler;
import org.apache.camel.component.undertow.handlers.NotFoundHandler;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.spi.RestApiConsumerFactory;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.RestConsumerFactory;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.UnsafeUriCharactersEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents the component that manages {@link UndertowEndpoint}.
 */
public class UndertowComponent extends UriEndpointComponent implements RestConsumerFactory, RestApiConsumerFactory {
    private static final Logger LOG = LoggerFactory.getLogger(UndertowEndpoint.class);

    private UndertowHttpBinding undertowHttpBinding = new DefaultUndertowHttpBinding();
    private final Map<Integer, UndertowRegistry> serversRegistry = new HashMap<Integer, UndertowRegistry>();

    public UndertowComponent() {
        super(UndertowEndpoint.class);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        URI uriHttpUriAddress = new URI(UnsafeUriCharactersEncoder.encodeHttpURI(remaining));
        URI endpointUri = URISupport.createRemainingURI(uriHttpUriAddress, parameters);

        // any additional channel options
        Map<String, Object> options = IntrospectionSupport.extractProperties(parameters, "option.");

        // create the endpoint first
        UndertowEndpoint endpoint = createEndpointInstance(endpointUri, this);
        endpoint.setUndertowHttpBinding(undertowHttpBinding);
        setProperties(endpoint, parameters);
        if (options != null) {
            endpoint.setOptions(options);
        }

        // then re-create the http uri with the remaining parameters which the endpoint did not use
        URI httpUri = URISupport.createRemainingURI(
                new URI(uriHttpUriAddress.getScheme(),
                        uriHttpUriAddress.getUserInfo(),
                        uriHttpUriAddress.getHost(),
                        uriHttpUriAddress.getPort(),
                        uriHttpUriAddress.getPath(),
                        uriHttpUriAddress.getQuery(),
                        uriHttpUriAddress.getFragment()),
                parameters);
        endpoint.setHttpURI(httpUri);

        return endpoint;
    }

    protected UndertowEndpoint createEndpointInstance(URI endpointUri, UndertowComponent component) throws URISyntaxException {
        return new UndertowEndpoint(endpointUri.toString(), component);
    }

    @Override
    public Consumer createConsumer(CamelContext camelContext, Processor processor, String verb, String basePath, String uriTemplate,
                                   String consumes, String produces, RestConfiguration configuration, Map<String, Object> parameters) throws Exception {
        return doCreateConsumer(camelContext, processor, verb, basePath, uriTemplate, consumes, produces, configuration, parameters, false);
    }

    @Override
    public Consumer createApiConsumer(CamelContext camelContext, Processor processor, String contextPath,
                                      RestConfiguration configuration, Map<String, Object> parameters) throws Exception {
        // reuse the createConsumer method we already have. The api need to use GET and match on uri prefix
        return doCreateConsumer(camelContext, processor, "GET", contextPath, null, null, null, configuration, parameters, true);
    }

    Consumer doCreateConsumer(CamelContext camelContext, Processor processor, String verb, String basePath, String uriTemplate,
                              String consumes, String produces, RestConfiguration configuration, Map<String, Object> parameters, boolean api) throws Exception {
        String path = basePath;
        if (uriTemplate != null) {
            // make sure to avoid double slashes
            if (uriTemplate.startsWith("/")) {
                path = path + uriTemplate;
            } else {
                path = path + "/" + uriTemplate;
            }
        }
        path = FileUtil.stripLeadingSeparator(path);
        String scheme = "http";
        String host = "localhost";
        int port = 0;

        RestConfiguration config = configuration;
        if (config == null) {
            config = getCamelContext().getRestConfiguration("undertow", true);
        }
        if (config.getScheme() != null) {
            scheme = config.getScheme();
        }
        if (config.getHost() != null) {
            host = config.getHost();
        }
        int num = config.getPort();
        if (num > 0) {
            port = num;
        }

        Map<String, Object> map = new HashMap<String, Object>();
        // build query string, and append any endpoint configuration properties
        if (config.getComponent() == null || config.getComponent().equals("undertow")) {
            // setup endpoint options
            if (config.getEndpointProperties() != null && !config.getEndpointProperties().isEmpty()) {
                map.putAll(config.getEndpointProperties());
            }
        }

        String query = URISupport.createQueryString(map);

        String url;
        if (api) {
            url = "undertow:%s://%s:%s/%s?matchOnUriPrefix=true";
        } else {
            url = "undertow:%s://%s:%s/%s";
        }

        url = String.format(url, scheme, host, port, path);

        if (!query.isEmpty()) {
            url = url + "&" + query;
        }

        UndertowEndpoint endpoint = camelContext.getEndpoint(url, UndertowEndpoint.class);
        setProperties(endpoint, parameters);

        Consumer consumer = endpoint.createConsumer(processor);
        return consumer;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        serversRegistry.clear();
    }

    public void registerConsumer(UndertowConsumer consumer) {
        int port = consumer.getEndpoint().getHttpURI().getPort();
        if (serversRegistry.containsKey(port)) {
            //server listens on port, we need add configuration for path
            UndertowRegistry undertowRegistry = serversRegistry.get(port);
            undertowRegistry.registerConsumer(consumer);
        } else {
            //create new server to listen on specified port
            serversRegistry.put(port, new UndertowRegistry(consumer, port));
        }
    }

    public void unregisterConsumer(UndertowConsumer consumer) {
        int port = consumer.getEndpoint().getHttpURI().getPort();
        if (serversRegistry.containsKey(port)) {
            serversRegistry.get(port).unregisterConsumer(consumer);
        }
        if (serversRegistry.get(port).isEmpty()) {
            //if there no Consumer left, we can shut down server
            Undertow server = serversRegistry.get(port).getServer();
            if (server != null) {
                server.stop();
            }
            serversRegistry.remove(port);
        } else {
            //call startServer to rebuild otherwise
            startServer(consumer);
        }
    }

    public void startServer(UndertowConsumer consumer) {
        int port = consumer.getEndpoint().getHttpURI().getPort();
        LOG.info("Starting server on port: {}", port);
        UndertowRegistry undertowRegistry = serversRegistry.get(port);
        if (undertowRegistry.getServer() != null) {
            //server is running, we need to stop it first and then rebuild
            undertowRegistry.getServer().stop();
        }
        Undertow newServer = rebuildServer(undertowRegistry);
        newServer.start();
        undertowRegistry.setServer(newServer);
    }

    protected Undertow rebuildServer(UndertowRegistry registy) {
        Undertow.Builder result = Undertow.builder();
        if (registy.getSslContext() != null) {
            result = result.addHttpsListener(registy.getPort(), registy.getHost(), registy.getSslContext());
        } else {
            result = result.addHttpListener(registy.getPort(), registy.getHost());
        }
        PathHandler path = Handlers.path(new NotFoundHandler());
        for (URI key : registy.getConsumersRegistry().keySet()) {
            UndertowConsumer consumer = registy.getConsumersRegistry().get(key);
            URI httpUri = consumer.getEndpoint().getHttpURI();
            HttpCamelHandler handler = new HttpCamelHandler(consumer);
            if (consumer.getEndpoint().getMatchOnUriPrefix()) {
                path.addPrefixPath(httpUri.getPath(), handler);
            } else {
                path.addExactPath(httpUri.getPath(), handler);
            }
            LOG.debug("Rebuild for path: {}", httpUri.getPath());
        }
        result = result.setHandler(path);
        return result.build();
    }

    public UndertowHttpBinding getUndertowHttpBinding() {
        return undertowHttpBinding;
    }

    /**
     * To use a custom HttpBinding to control the mapping between Camel message and HttpClient.
     */
    public void setUndertowHttpBinding(UndertowHttpBinding undertowHttpBinding) {
        this.undertowHttpBinding = undertowHttpBinding;
    }
}
