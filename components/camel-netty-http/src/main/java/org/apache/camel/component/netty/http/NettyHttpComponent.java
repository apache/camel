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
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.component.netty.NettyComponent;
import org.apache.camel.component.netty.NettyConfiguration;
import org.apache.camel.component.netty.NettyServerBootstrapConfiguration;
import org.apache.camel.component.netty.http.handlers.HttpServerMultiplexChannelHandler;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.ServiceHelper;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.UnsafeUriCharactersEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Netty HTTP based component.
 */
public class NettyHttpComponent extends NettyComponent implements HeaderFilterStrategyAware {

    private static final Logger LOG = LoggerFactory.getLogger(NettyHttpComponent.class);

    // factories which is created by this component and therefore manage their lifecycles
    private final Map<Integer, HttpServerConsumerChannelFactory> multiplexChannelHandlers = new HashMap<Integer, HttpServerConsumerChannelFactory>();
    private final Map<String, HttpServerBootstrapFactory> bootstrapFactories = new HashMap<String, HttpServerBootstrapFactory>();
    private NettyHttpBinding nettyHttpBinding;
    private HeaderFilterStrategy headerFilterStrategy;
    private NettyHttpSecurityConfiguration securityConfiguration;

    public NettyHttpComponent() {
        // use the http configuration and filter strategy
        setConfiguration(new NettyHttpConfiguration());
        setHeaderFilterStrategy(new NettyHttpHeaderFilterStrategy());
        setNettyHttpBinding(new DefaultNettyHttpBinding(getHeaderFilterStrategy()));
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        NettyConfiguration config;
        if (getConfiguration() != null) {
            config = getConfiguration().copy();
        } else {
            config = new NettyHttpConfiguration();
        }

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

        // validate config
        config.validateConfiguration();

        // are we using a shared http server?
        NettySharedHttpServer shared = resolveAndRemoveReferenceParameter(parameters, "nettySharedHttpServer", NettySharedHttpServer.class);
        if (shared != null) {
            // use port number from the shared http server
            LOG.debug("Using NettySharedHttpServer: {} with port: {}", shared, shared.getPort());
            config.setPort(shared.getPort());
        }

        NettyHttpEndpoint answer = new NettyHttpEndpoint(remaining, this, config);
        answer.setTimer(getTimer());
        setProperties(answer.getConfiguration(), parameters);

        // any leftover parameters is uri parameters
        if (!parameters.isEmpty()) {
            String query = URISupport.createQueryString(parameters);
            answer.setUriParameters(query);
        }

        // set component options on endpoint as defaults
        if (answer.getNettyHttpBinding() == null) {
            answer.setNettyHttpBinding(getNettyHttpBinding());
        }
        if (answer.getHeaderFilterStrategy() == null) {
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
        String safe = UnsafeUriCharactersEncoder.encode(remaining);
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

    public void setNettyHttpBinding(NettyHttpBinding nettyHttpBinding) {
        this.nettyHttpBinding = nettyHttpBinding;
    }

    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return headerFilterStrategy;
    }

    public void setHeaderFilterStrategy(HeaderFilterStrategy headerFilterStrategy) {
        this.headerFilterStrategy = headerFilterStrategy;
    }

    public NettyHttpSecurityConfiguration getSecurityConfiguration() {
        return securityConfiguration;
    }

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
    protected void doStop() throws Exception {
        super.doStop();

        ServiceHelper.stopServices(bootstrapFactories.values());
        bootstrapFactories.clear();

        ServiceHelper.stopService(multiplexChannelHandlers.values());
        multiplexChannelHandlers.clear();
    }
}
