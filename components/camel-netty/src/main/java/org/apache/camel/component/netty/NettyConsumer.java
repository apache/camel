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
package org.apache.camel.component.netty;

import org.apache.camel.CamelContext;
import org.apache.camel.Processor;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.service.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NettyConsumer extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(NettyConsumer.class);

    private CamelContext context;
    private NettyConfiguration configuration;
    private NettyServerBootstrapFactory nettyServerBootstrapFactory;

    public NettyConsumer(NettyEndpoint nettyEndpoint, Processor processor, NettyConfiguration configuration) {
        super(nettyEndpoint, processor);
        this.context = this.getEndpoint().getCamelContext();
        this.configuration = configuration;
        setNettyServerBootstrapFactory(configuration.getNettyServerBootstrapFactory());
        setExceptionHandler(new NettyConsumerExceptionHandler(this));
    }

    @Override
    public NettyEndpoint getEndpoint() {
        return (NettyEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        LOG.debug("Netty consumer binding to: {}", configuration.getAddress());

        if (nettyServerBootstrapFactory == null) {
            // setup pipeline factory
            ServerInitializerFactory pipelineFactory;
            ServerInitializerFactory factory = configuration.getServerInitializerFactory();
            if (factory != null) {
                pipelineFactory = factory.createPipelineFactory(this);
            } else {
                pipelineFactory = new DefaultServerInitializerFactory(this);
            }

            if (isTcp()) {
                if (configuration.isClientMode()) {
                    nettyServerBootstrapFactory = new ClientModeTCPNettyServerBootstrapFactory();
                } else {
                    nettyServerBootstrapFactory = new SingleTCPNettyServerBootstrapFactory();
                }
            } else {
                nettyServerBootstrapFactory = new SingleUDPNettyServerBootstrapFactory();
            }
            nettyServerBootstrapFactory.init(context, configuration, pipelineFactory);
        }

        ServiceHelper.startService(nettyServerBootstrapFactory);

        LOG.info("Netty consumer bound to: {}", configuration.getAddress());
    }

    @Override
    protected void doStop() throws Exception {
        LOG.debug("Netty consumer unbinding from: {}", configuration.getAddress());

        ServiceHelper.stopService(nettyServerBootstrapFactory);

        LOG.info("Netty consumer unbound from: {}", configuration.getAddress());

        super.doStop();
    }

    public CamelContext getContext() {
        return context;
    }

    public NettyConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(NettyConfiguration configuration) {
        this.configuration = configuration;
    }

    public NettyServerBootstrapFactory getNettyServerBootstrapFactory() {
        return nettyServerBootstrapFactory;
    }

    public void setNettyServerBootstrapFactory(NettyServerBootstrapFactory nettyServerBootstrapFactory) {
        this.nettyServerBootstrapFactory = nettyServerBootstrapFactory;
    }

    protected boolean isTcp() {
        return configuration.getProtocol().equalsIgnoreCase("tcp");
    }

}
