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
package org.apache.camel.coap;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.RestConsumerFactory;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.HostUtils;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.eclipse.californium.core.CoapServer;
import org.eclipse.californium.core.network.config.NetworkConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents the component that manages {@link CoAPEndpoint}.
 */
public class CoAPComponent extends UriEndpointComponent implements RestConsumerFactory {
    static final int DEFAULT_PORT = 5684;
    private static final Logger LOG = LoggerFactory.getLogger(CoAPComponent.class);

    final Map<Integer, CoapServer> servers = new ConcurrentHashMap<>();

    public CoAPComponent() {
        super(CoAPEndpoint.class);
    }

    public CoAPComponent(CamelContext context) {
        super(context, CoAPEndpoint.class);
    }

    public synchronized CoapServer getServer(int port) {
        CoapServer server = servers.get(port);
        if (server == null && port == -1) {
            server = getServer(DEFAULT_PORT);
        }
        if (server == null) {
            NetworkConfig config = new NetworkConfig();
            //FIXME- configure the network stuff
            server = new CoapServer(config, port);
            servers.put(port, server);
            if (this.isStarted()) {
                server.start();
            }
        }
        return server;
    }

    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        Endpoint endpoint = new CoAPEndpoint(uri, this);
        setProperties(endpoint, parameters);
        return endpoint;
    }

    @Override
    public Consumer createConsumer(CamelContext camelContext, Processor processor, String verb, String basePath,
            String uriTemplate, String consumes, String produces, RestConfiguration configuration, Map<String, Object> parameters) throws Exception {

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

        RestConfiguration config = configuration;
        if (config == null) {
            config = getCamelContext().getRestConfiguration("coap", true);
        }

        if (config.isEnableCORS()) {
            LOG.info("CORS configuration will be ignored as CORS is not supported by the CoAP component");
        }

        String host = config.getHost();
        if (ObjectHelper.isEmpty(host)) {
            if (config.getRestHostNameResolver() == RestConfiguration.RestHostNameResolver.allLocalIp) {
                host = "0.0.0.0";
            } else if (config.getRestHostNameResolver() == RestConfiguration.RestHostNameResolver.localHostName) {
                host = HostUtils.getLocalHostName();
            } else if (config.getRestHostNameResolver() == RestConfiguration.RestHostNameResolver.localIp) {
                host = HostUtils.getLocalIp();
            }
        }

        Map<String, Object> map = new HashMap<>();
        // setup endpoint options
        if (config.getEndpointProperties() != null && !config.getEndpointProperties().isEmpty()) {
            map.putAll(config.getEndpointProperties());
        }

        String scheme = config.getScheme() == null ? "coap" : config.getScheme();
        String query = URISupport.createQueryString(map);
        int port = 0;

        int num = config.getPort();
        if (num > 0) {
            port = num;
        }

        // prefix path with context-path if configured in rest-dsl configuration
        String contextPath = config.getContextPath();
        if (ObjectHelper.isNotEmpty(contextPath)) {
            contextPath = FileUtil.stripTrailingSeparator(contextPath);
            contextPath = FileUtil.stripLeadingSeparator(contextPath);
            if (ObjectHelper.isNotEmpty(contextPath)) {
                path = contextPath + "/" + path;
            }
        }

        String restrict = verb.toUpperCase(Locale.US);
        String url = String.format("%s://%s:%d/%s?coapMethodRestrict=%s", scheme, host, port, path, restrict);

        if (!query.isEmpty()) {
            url += "&" + query;
        }

        CoAPEndpoint endpoint = camelContext.getEndpoint(url, CoAPEndpoint.class);
        setProperties(endpoint, parameters);

        // configure consumer properties
        Consumer consumer = endpoint.createConsumer(processor);
        if (config.getConsumerProperties() != null && !config.getConsumerProperties().isEmpty()) {
            setProperties(consumer, config.getConsumerProperties());
        }
        return consumer;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        for (CoapServer s : servers.values()) {
            s.start();
        }
    }

    @Override
    protected void doStop() throws Exception {
        for (CoapServer s : servers.values()) {
            s.stop();
        }
        super.doStop();
    }
}
