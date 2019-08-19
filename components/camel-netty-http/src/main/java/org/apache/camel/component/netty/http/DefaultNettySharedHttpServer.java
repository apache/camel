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
package org.apache.camel.component.netty.http;

import java.util.concurrent.ThreadFactory;
import java.util.regex.Matcher;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import org.apache.camel.CamelContext;
import org.apache.camel.component.netty.NettyServerBootstrapFactory;
import org.apache.camel.component.netty.http.handlers.HttpServerMultiplexChannelHandler;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.concurrent.CamelThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A default {@link NettySharedHttpServer} to make sharing Netty server in Camel applications easier.
 */
public class DefaultNettySharedHttpServer extends ServiceSupport implements NettySharedHttpServer {

    // TODO: option to enlist in JMX

    public static final String DEFAULT_PATTERN = "Camel Thread ##counter# - #name#:#port#";
    private static final Logger LOG = LoggerFactory.getLogger(DefaultNettySharedHttpServer.class);

    private NettySharedHttpServerBootstrapConfiguration configuration;
    private HttpServerConsumerChannelFactory channelFactory;
    private HttpServerBootstrapFactory bootstrapFactory;
    private CamelContext camelContext;
    private boolean startServer = true;
    private String threadPattern = DEFAULT_PATTERN;

    @Override
    public void setNettyServerBootstrapConfiguration(NettySharedHttpServerBootstrapConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public int getPort() {
        return configuration != null ? configuration.getPort() : -1;
    }

    @Override
    public HttpServerConsumerChannelFactory getConsumerChannelFactory() {
        return channelFactory;
    }

    @Override
    public NettyServerBootstrapFactory getServerBootstrapFactory() {
        return bootstrapFactory;
    }

    @Override
    public int getConsumersSize() {
        if (channelFactory != null) {
            return channelFactory.consumers();
        } else {
            return -1;
        }
    }

    @Override
    public void setStartServer(boolean startServer) {
        this.startServer = startServer;
    }

    @Override
    public void setThreadNamePattern(String pattern) {
        this.threadPattern = pattern;
    }

    @Override
    protected void doStart() throws Exception {
        ObjectHelper.notNull(configuration, "setNettyServerBootstrapConfiguration() must be called with a NettyServerBootstrapConfiguration instance", this);

        // port must be set
        if (configuration.getPort() <= 0) {
            throw new IllegalArgumentException("Port must be configured on NettySharedHttpServerBootstrapConfiguration " + configuration);
        }
        // hostname must be set
        if (ObjectHelper.isEmpty(configuration.getHost())) {
            throw new IllegalArgumentException("Host must be configured on NettySharedHttpServerBootstrapConfiguration " + configuration);
        }

        LOG.debug("NettySharedHttpServer using configuration: {}", configuration);

        // force using tcp as the underlying transport
        configuration.setProtocol("tcp");

        channelFactory = new HttpServerMultiplexChannelHandler();
        channelFactory.init(configuration.getPort());

        ChannelInitializer<Channel> pipelineFactory = new HttpServerSharedInitializerFactory(configuration, channelFactory, camelContext);

        // thread factory and pattern
        String port = Matcher.quoteReplacement("" + configuration.getPort());
        String pattern = threadPattern;
        pattern = pattern.replaceFirst("#port#", port);
        ThreadFactory tf = new CamelThreadFactory(pattern, "NettySharedHttpServer", true);

        // create bootstrap factory and disable compatible check as its shared among the consumers
        bootstrapFactory = new HttpServerBootstrapFactory(channelFactory, false);
        bootstrapFactory.init(tf, configuration, pipelineFactory);

        ServiceHelper.startService(channelFactory);

        if (startServer) {
            LOG.info("Starting NettySharedHttpServer on {}:{}", configuration.getHost(), configuration.getPort());
            ServiceHelper.startService(bootstrapFactory);
        }
    }

    @Override
    protected void doStop() throws Exception {
        LOG.info("Stopping NettySharedHttpServer on {}:{}", configuration.getHost(), configuration.getPort());
        ServiceHelper.stopService(bootstrapFactory, channelFactory);
    }
}
