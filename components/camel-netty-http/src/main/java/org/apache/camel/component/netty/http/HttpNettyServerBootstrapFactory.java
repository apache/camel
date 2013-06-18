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
import org.apache.camel.component.netty.ServerPipelineFactory;
import org.apache.camel.component.netty.SingleTCPNettyServerBootstrapFactory;

public class HttpNettyServerBootstrapFactory extends SingleTCPNettyServerBootstrapFactory {

    private final NettyHttpComponent component;
    private final int port;

    public HttpNettyServerBootstrapFactory(CamelContext camelContext, NettyConfiguration nettyConfiguration, ServerPipelineFactory pipelineFactory,
                                           NettyHttpComponent component) {
        super(camelContext, nettyConfiguration, pipelineFactory);
        this.component = component;
        this.port = nettyConfiguration.getPort();
    }

    public void addConsumer(NettyConsumer consumer) {
        component.getMultiplexChannelHandler(port).addConsumer((NettyHttpConsumer) consumer);
    }

    @Override
    public void removeConsumer(NettyConsumer consumer) {
        component.getMultiplexChannelHandler(port).removeConsumer((NettyHttpConsumer) consumer);
    }

    @Override
    public void stop() throws Exception {
        // only stop if no more active consumers
        int consumers = component.getMultiplexChannelHandler(port).consumers();
        if (consumers == 0) {
            super.stop();
        } else {
            LOG.info("There are {} active consumers, so cannot stop {} yet.", consumers, HttpNettyServerBootstrapFactory.class.getName());
        }
    }

}
