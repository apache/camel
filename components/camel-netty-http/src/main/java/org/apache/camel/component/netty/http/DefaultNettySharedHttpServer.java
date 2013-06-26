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

import java.util.concurrent.ThreadFactory;
import java.util.regex.Matcher;

import org.apache.camel.component.netty.NettyServerBootstrapFactory;
import org.apache.camel.component.netty.http.handlers.HttpServerMultiplexChannelHandler;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;
import org.apache.camel.util.concurrent.CamelThreadFactory;
import org.jboss.netty.channel.ChannelPipelineFactory;
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
    private ClassResolver classResolver;
    private boolean startServer = true;
    private String threadPattern = DEFAULT_PATTERN;

    public void setNettyServerBootstrapConfiguration(NettySharedHttpServerBootstrapConfiguration configuration) {
        this.configuration = configuration;
    }

    public void setClassResolver(ClassResolver classResolver) {
        this.classResolver = classResolver;
    }

    public int getPort() {
        return configuration != null ? configuration.getPort() : -1;
    }

    public HttpServerConsumerChannelFactory getConsumerChannelFactory() {
        return channelFactory;
    }

    public NettyServerBootstrapFactory getServerBootstrapFactory() {
        return bootstrapFactory;
    }

    public int getConsumersSize() {
        if (channelFactory != null) {
            return channelFactory.consumers();
        } else {
            return -1;
        }
    }

    public void setStartServer(boolean startServer) {
        this.startServer = startServer;
    }

    public void setThreadNamePattern(String pattern) {
        this.threadPattern = pattern;
    }

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

        ChannelPipelineFactory pipelineFactory = new HttpServerSharedPipelineFactory(configuration, channelFactory, classResolver);

        // thread factory and pattern
        String port = Matcher.quoteReplacement("" + configuration.getPort());
        String pattern = threadPattern;
        pattern = pattern.replaceFirst("#port#", port);
        ThreadFactory tf = new CamelThreadFactory(pattern, "NettySharedHttpServer", true);

        // create bootstrap factory and disable compatible check as its shared among the consumers
        bootstrapFactory = new HttpServerBootstrapFactory(channelFactory, false);
        bootstrapFactory.init(tf, configuration, pipelineFactory);

        ServiceHelper.startServices(channelFactory);

        if (startServer) {
            LOG.info("Starting NettySharedHttpServer on {}:{}", configuration.getHost(), configuration.getPort());
            ServiceHelper.startServices(bootstrapFactory);
        }
    }

    @Override
    protected void doStop() throws Exception {
        LOG.info("Stopping NettySharedHttpServer on {}:{}", configuration.getHost(), configuration.getPort());
        ServiceHelper.stopServices(bootstrapFactory, channelFactory);
    }
}
