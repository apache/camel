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
import org.apache.camel.component.netty.http.handlers.HttpServerMultiplexChannelHandler;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.apache.camel.util.ServiceHelper;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.UnsafeUriCharactersEncoder;

/**
 * Netty HTTP based component.
 */
public class NettyHttpComponent extends NettyComponent implements HeaderFilterStrategyAware {

    // TODO: support on consumer
    // - validate routes on same port cannot have different SSL etc
    // - urlrewrite

    private final Map<Integer, HttpServerMultiplexChannelHandler> multiplexChannelHandlers = new HashMap<Integer, HttpServerMultiplexChannelHandler>();
    private final Map<String, HttpServerBootstrapFactory> bootstrapFactories = new HashMap<String, HttpServerBootstrapFactory>();
    private NettyHttpBinding nettyHttpBinding;
    private HeaderFilterStrategy headerFilterStrategy;

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

        config = parseConfiguration(config, remaining, parameters);

        // validate config
        config.validateConfiguration();

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

    public synchronized HttpServerMultiplexChannelHandler getMultiplexChannelHandler(int port) {
        HttpServerMultiplexChannelHandler answer = multiplexChannelHandlers.get(port);
        if (answer == null) {
            answer = new HttpServerMultiplexChannelHandler(port);
            multiplexChannelHandlers.put(port, answer);
        }
        return answer;
    }

    protected synchronized HttpServerBootstrapFactory getOrCreateHttpNettyServerBootstrapFactory(NettyHttpConsumer consumer) {
        String key = consumer.getConfiguration().getAddress();
        HttpServerBootstrapFactory answer = bootstrapFactories.get(key);
        if (answer == null) {
            answer = new HttpServerBootstrapFactory(this);
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
