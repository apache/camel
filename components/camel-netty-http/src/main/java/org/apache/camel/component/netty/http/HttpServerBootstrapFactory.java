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

import org.apache.camel.CamelContext;
import org.apache.camel.component.netty.NettyConfiguration;
import org.apache.camel.component.netty.NettyConsumer;
import org.apache.camel.component.netty.SingleTCPNettyServerBootstrapFactory;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpServerBootstrapFactory extends SingleTCPNettyServerBootstrapFactory {

    private static final Logger LOG = LoggerFactory.getLogger(HttpServerBootstrapFactory.class);
    private final NettyHttpComponent component;
    private int port;

    public HttpServerBootstrapFactory(NettyHttpComponent component) {
        this.component = component;
    }

    @Override
    public void init(CamelContext camelContext, NettyConfiguration configuration, ChannelPipelineFactory pipelineFactory) {
        super.init(camelContext, configuration, pipelineFactory);
        this.port = configuration.getPort();

        LOG.info("BootstrapFactory on port {} is using configuration: {}", port, configuration);
    }

    public void addConsumer(NettyConsumer consumer) {
        if (LOG.isDebugEnabled()) {
            NettyHttpConsumer httpConsumer = (NettyHttpConsumer) consumer;
            LOG.debug("BootstrapFactory on port {} is adding consumer with context-path {}", port, httpConsumer.getConfiguration().getPath());
        }
        component.getMultiplexChannelHandler(port).addConsumer((NettyHttpConsumer) consumer);
    }

    @Override
    public void removeConsumer(NettyConsumer consumer) {
        if (LOG.isDebugEnabled()) {
            NettyHttpConsumer httpConsumer = (NettyHttpConsumer) consumer;
            LOG.debug("BootstrapFactory on port {} is removing consumer with context-path {}", port, httpConsumer.getConfiguration().getPath());
        }
        component.getMultiplexChannelHandler(port).removeConsumer((NettyHttpConsumer) consumer);
    }

    @Override
    protected void doStart() throws Exception {
        LOG.debug("BootstrapFactory on port {} is starting", port);
        super.doStart();
    }

    @Override
    public void stop() throws Exception {
        // only stop if no more active consumers
        int consumers = component.getMultiplexChannelHandler(port).consumers();
        if (consumers == 0) {
            LOG.debug("BootstrapFactory on port {} is stopping", port);
            super.stop();
        } else {
            LOG.debug("BootstrapFactory on port {} has {} registered consumers, so cannot stop yet.", port, consumers);
        }
    }

}
