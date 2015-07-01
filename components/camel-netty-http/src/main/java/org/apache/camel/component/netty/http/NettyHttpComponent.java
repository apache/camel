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
package org.apache.camel.component.netty.http;

import java.net.URI;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.component.netty.NettyComponent;
import org.apache.camel.component.netty.NettyConfiguration;
import org.apache.camel.component.netty.NettyServerBootstrapConfiguration;
import org.apache.camel.component.netty.http.handlers.HttpServerMultiplexChannelHandler;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.RestConsumerFactory;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.HostUtils;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.UnsafeUriCharactersEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Netty HTTP based component.
 */
public class NettyHttpComponent extends NettyComponent implements HeaderFilterStrategyAware, RestConsumerFactory {

    private static final Logger LOG = LoggerFactory.getLogger(NettyHttpComponent.class);

    // factories which is created by this component and therefore manage their lifecycles
    private final Map<Integer, HttpServerConsumerChannelFactory> multiplexChannelHandlers = new HashMap<Integer, HttpServerConsumerChannelFactory>();
    private final Map<String, HttpServerBootstrapFactory> bootstrapFactories = new HashMap<String, HttpServerBootstrapFactory>();
    private NettyHttpBinding nettyHttpBinding;
    private HeaderFilterStrategy headerFilterStrategy;
    private NettyHttpSecurityConfiguration securityConfiguration;

    public NettyHttpComponent() {
        // use the http configuration and filter strategy
        super(NettyHttpEndpoint.class);
        setConfiguration(new NettyHttpConfiguration());
        setHeaderFilterStrategy(new NettyHttpHeaderFilterStrategy());
        // use the binding that supports Rest DSL
        setNettyHttpBinding(new RestNettyHttpBinding(getHeaderFilterStrategy()));
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        NettyConfiguration config;
        if (getConfiguration() != null) {
            config = getConfiguration().copy();
        } else {
            config = new NettyHttpConfiguration();
        }

        HeaderFilterStrategy headerFilterStrategy = resolveAndRemoveReferenceParameter(parameters, "headerFilterStrategy", HeaderFilterStrategy.class);

        // merge any custom bootstrap configuration on the config
        NettyServerBootstrapConfiguration bootstrapConfiguration = resolveAndRemoveReferenceParameter(parameters, "bootstrapConfiguration", NettyServerBootstrapConfiguration.class);
        if (bootstrapConfiguration != null) {
            Map<String, Object> options = new HashMap<String, Object>();
            if (IntrospectionSupport.getProperties(bootstrapConfiguration, options, null, false)) {
                IntrospectionSupport.setProperties(getCamelContext().getTypeConverter(), config, options);
            }
        }

        // any custom security configuration
        NettyHttpSecurityConfiguration securityConfiguration = resolveAndRemoveReferenceParameter(parameters, "securityConfiguration", NettyHttpSecurityConfiguration.class);
        Map<String, Object> securityOptions = IntrospectionSupport.extractProperties(parameters, "securityConfiguration.");

        config = parseConfiguration(config, remaining, parameters);
        setProperties(config, parameters);

        // validate config
        config.validateConfiguration();

        // are we using a shared http server?
        NettySharedHttpServer shared = resolveAndRemoveReferenceParameter(parameters, "nettySharedHttpServer", NettySharedHttpServer.class);
        if (shared != null) {
            // use port number from the shared http server
            LOG.debug("Using NettySharedHttpServer: {} with port: {}", shared, shared.getPort());
            config.setPort(shared.getPort());
        }

        // create the address uri which includes the remainder parameters (which is not configuration parameters for this component)
        URI u = new URI(UnsafeUriCharactersEncoder.encodeHttpURI(remaining));
        
        String addressUri = URISupport.createRemainingURI(u, parameters).toString();

        NettyHttpEndpoint answer = new NettyHttpEndpoint(addressUri, this, config);
        answer.setTimer(getTimer());

        // must use a copy of the binding on the endpoint to avoid sharing same instance that can cause side-effects
        if (answer.getNettyHttpBinding() == null) {
            Object binding = getNettyHttpBinding();
            if (binding instanceof RestNettyHttpBinding) {
                NettyHttpBinding copy = ((RestNettyHttpBinding) binding).copy();
                answer.setNettyHttpBinding(copy);
            } else if (binding instanceof DefaultNettyHttpBinding) {
                NettyHttpBinding copy = ((DefaultNettyHttpBinding) binding).copy();
                answer.setNettyHttpBinding(copy);
            }
        }
        if (headerFilterStrategy != null) {
            answer.setHeaderFilterStrategy(headerFilterStrategy);
        } else if (answer.getHeaderFilterStrategy() == null) {
            answer.setHeaderFilterStrategy(getHeaderFilterStrategy());
        }

        if (securityConfiguration != null) {
            answer.setSecurityConfiguration(securityConfiguration);
        } else if (answer.getSecurityConfiguration() == null) {
            answer.setSecurityConfiguration(getSecurityConfiguration());
        }

        // configure any security options
        if (securityOptions != null && !securityOptions.isEmpty()) {
            securityConfiguration = answer.getSecurityConfiguration();
            if (securityConfiguration == null) {
                securityConfiguration = new NettyHttpSecurityConfiguration();
                answer.setSecurityConfiguration(securityConfiguration);
            }
            setProperties(securityConfiguration, securityOptions);
            validateParameters(uri, securityOptions, null);
        }

        answer.setNettySharedHttpServer(shared);
        return answer;
    }

    @Override
    protected NettyConfiguration parseConfiguration(NettyConfiguration configuration, String remaining, Map<String, Object> parameters) throws Exception {
        // ensure uri is encoded to be valid
        String safe = UnsafeUriCharactersEncoder.encodeHttpURI(remaining);
        URI uri = new URI(safe);
        configuration.parseURI(uri, parameters, this, "http", "https");

        // force using tcp as the underlying transport
        configuration.setProtocol("tcp");
        configuration.setTextline(false);

        if (configuration instanceof NettyHttpConfiguration) {
            ((NettyHttpConfiguration) configuration).setPath(uri.getPath());
        }

        return configuration;
    }

    public NettyHttpBinding getNettyHttpBinding() {
        return nettyHttpBinding;
    }

    /**
     * To use a custom org.apache.camel.component.netty.http.NettyHttpBinding for binding to/from Netty and Camel Message API.
     */
    public void setNettyHttpBinding(NettyHttpBinding nettyHttpBinding) {
        this.nettyHttpBinding = nettyHttpBinding;
    }

    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return headerFilterStrategy;
    }

    /**
     * To use a custom org.apache.camel.spi.HeaderFilterStrategy to filter headers.
     */
    public void setHeaderFilterStrategy(HeaderFilterStrategy headerFilterStrategy) {
        this.headerFilterStrategy = headerFilterStrategy;
    }

    public NettyHttpSecurityConfiguration getSecurityConfiguration() {
        return securityConfiguration;
    }

    /**
     * Refers to a org.apache.camel.component.netty.http.NettyHttpSecurityConfiguration for configuring secure web resources.
     */
    public void setSecurityConfiguration(NettyHttpSecurityConfiguration securityConfiguration) {
        this.securityConfiguration = securityConfiguration;
    }

    public synchronized HttpServerConsumerChannelFactory getMultiplexChannelHandler(int port) {
        HttpServerConsumerChannelFactory answer = multiplexChannelHandlers.get(port);
        if (answer == null) {
            answer = new HttpServerMultiplexChannelHandler();
            answer.init(port);
            multiplexChannelHandlers.put(port, answer);
        }
        return answer;
    }

    protected synchronized HttpServerBootstrapFactory getOrCreateHttpNettyServerBootstrapFactory(NettyHttpConsumer consumer) {
        String key = consumer.getConfiguration().getAddress();
        HttpServerBootstrapFactory answer = bootstrapFactories.get(key);
        if (answer == null) {
            HttpServerConsumerChannelFactory channelFactory = getMultiplexChannelHandler(consumer.getConfiguration().getPort());
            answer = new HttpServerBootstrapFactory(channelFactory);
            answer.init(getCamelContext(), consumer.getConfiguration(), new HttpServerPipelineFactory(consumer));
            bootstrapFactories.put(key, answer);
        }
        return answer;
    }

    @Override
    public Consumer createConsumer(CamelContext camelContext, Processor processor, String verb, String basePath, String uriTemplate,
                                   String consumes, String produces, Map<String, Object> parameters) throws Exception {

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
        String host = "";
        int port = 0;

        // if no explicit port/host configured, then use port from rest configuration
        RestConfiguration config = getCamelContext().getRestConfiguration();
        if (config.getComponent() == null || config.getComponent().equals("netty-http")) {
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
        }

        // if no explicit hostname set then resolve the hostname
        if (ObjectHelper.isEmpty(host)) {
            if (config.getRestHostNameResolver() == RestConfiguration.RestHostNameResolver.localHostName) {
                host = HostUtils.getLocalHostName();
            } else if (config.getRestHostNameResolver() == RestConfiguration.RestHostNameResolver.localIp) {
                host = HostUtils.getLocalIp();
            }
        }

        Map<String, Object> map = new HashMap<String, Object>();
        // build query string, and append any endpoint configuration properties
        if (config != null && (config.getComponent() == null || config.getComponent().equals("netty-http"))) {
            // setup endpoint options
            if (config.getEndpointProperties() != null && !config.getEndpointProperties().isEmpty()) {
                map.putAll(config.getEndpointProperties());
            }
        }

        String query = URISupport.createQueryString(map);

        String url = "netty-http:%s://%s:%s/%s?httpMethodRestrict=%s";
        
        // must use upper case for restrict
        String restrict = verb.toUpperCase(Locale.US);
        // get the endpoint
        url = String.format(url, scheme, host, port, path, restrict);
        
        if (!query.isEmpty()) {
            url = url + "&" + query;
        }

        
        NettyHttpEndpoint endpoint = camelContext.getEndpoint(url, NettyHttpEndpoint.class);
        setProperties(endpoint, parameters);

        // configure consumer properties
        Consumer consumer = endpoint.createConsumer(processor);
        if (config != null && config.getConsumerProperties() != null && !config.getConsumerProperties().isEmpty()) {
            setProperties(consumer, config.getConsumerProperties());
        }

        return consumer;
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        ServiceHelper.stopServices(bootstrapFactories.values());
        bootstrapFactories.clear();

        ServiceHelper.stopService(multiplexChannelHandlers.values());
        multiplexChannelHandlers.clear();
    }
}
