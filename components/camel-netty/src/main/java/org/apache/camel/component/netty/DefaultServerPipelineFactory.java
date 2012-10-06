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
package org.apache.camel.component.netty;

import java.util.List;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.apache.camel.component.netty.handlers.ServerChannelHandler;
import org.apache.camel.component.netty.ssl.SSLEngineFactory;
import org.apache.camel.util.concurrent.CamelThreadFactory;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.execution.ExecutionHandler;
import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;
import org.jboss.netty.handler.ssl.SslHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultServerPipelineFactory extends ServerPipelineFactory {
    private static final transient Logger LOG = LoggerFactory.getLogger(DefaultServerPipelineFactory.class);

    private final NettyConsumer consumer;

    public DefaultServerPipelineFactory(NettyConsumer consumer) {
        this.consumer = consumer;
    }

    @Override
    public ChannelPipeline getPipeline() throws Exception {
        ChannelPipeline channelPipeline = Channels.pipeline();

        SslHandler sslHandler = configureServerSSLOnDemand(consumer);
        if (sslHandler != null) {
            LOG.debug("Server SSL handler configured and added as an interceptor against the ChannelPipeline");
            addToPipeline("ssl", channelPipeline, sslHandler);
        }

        List<ChannelHandler> encoders = consumer.getConfiguration().getEncoders();
        for (int x = 0; x < encoders.size(); x++) {
            ChannelHandler encoder = encoders.get(x);
            if (encoder instanceof ChannelHandlerFactory) {
                // use the factory to create a new instance of the channel as it may not be shareable
                encoder = ((ChannelHandlerFactory) encoder).newChannelHandler();
            }
            addToPipeline("encoder-" + x, channelPipeline, encoder);
        }

        List<ChannelHandler> decoders = consumer.getConfiguration().getDecoders();
        for (int x = 0; x < decoders.size(); x++) {
            ChannelHandler decoder = decoders.get(x);
            if (decoder instanceof ChannelHandlerFactory) {
                // use the factory to create a new instance of the channel as it may not be shareable
                decoder = ((ChannelHandlerFactory) decoder).newChannelHandler();
            }
            addToPipeline("decoder-" + x, channelPipeline, decoder);
        }

        if (consumer.getConfiguration().isOrderedThreadPoolExecutor()) {
            // use ordered thread pool, to ensure we process the events in order, and can send back
            // replies in the expected order. eg this is required by TCP.
            // and use a Camel thread factory so we have consistent thread namings
            String pattern = consumer.getContext().getExecutorServiceManager().getThreadNamePattern();
            ThreadFactory factory = new CamelThreadFactory(pattern, "NettyOrderedWorker", true);
            final ExecutionHandler executionHandler = new ExecutionHandler(
                    new OrderedMemoryAwareThreadPoolExecutor(consumer.getConfiguration().getMaximumPoolSize(),
                            0L, 0L, 30, TimeUnit.SECONDS, factory));
            // this must be added just before the ServerChannelHandler
            addToPipeline("executionHandler", channelPipeline, executionHandler);
            LOG.debug("Using OrderedMemoryAwareThreadPoolExecutor with core pool size: {}", consumer.getConfiguration().getMaximumPoolSize());
        }

        // our handler must be added last
        addToPipeline("handler", channelPipeline, new ServerChannelHandler(consumer));

        LOG.trace("Created ChannelPipeline: {}", channelPipeline);
        return channelPipeline;
    }

    private void addToPipeline(String name, ChannelPipeline pipeline, ChannelHandler handler) {
        pipeline.addLast(name, handler);
    }

    private SslHandler configureServerSSLOnDemand(NettyConsumer consumer) throws Exception {
        if (!consumer.getConfiguration().isSsl()) {
            return null;
        }

        if (consumer.getConfiguration().getSslHandler() != null) {
            return consumer.getConfiguration().getSslHandler();
        } else if (consumer.getConfiguration().getSslContextParameters() != null) {
            SSLContext context = consumer.getConfiguration().getSslContextParameters().createSSLContext();
            SSLEngine engine = context.createSSLEngine();
            engine.setUseClientMode(false);
            engine.setNeedClientAuth(consumer.getConfiguration().isNeedClientAuth());
            return new SslHandler(engine);
        } else {
            SSLEngineFactory sslEngineFactory = new SSLEngineFactory(
                consumer.getConfiguration().getKeyStoreFormat(),
                consumer.getConfiguration().getSecurityProvider(),
                consumer.getConfiguration().getKeyStoreFile(), 
                consumer.getConfiguration().getTrustStoreFile(), 
                consumer.getConfiguration().getPassphrase().toCharArray());
            SSLEngine sslEngine = sslEngineFactory.createServerSSLEngine();
            sslEngine.setUseClientMode(false);
            sslEngine.setNeedClientAuth(consumer.getConfiguration().isNeedClientAuth());
            return new SslHandler(sslEngine);
        }
    }

    @Override
    public ServerPipelineFactory createPipelineFactory(NettyConsumer consumer) {
        return new DefaultServerPipelineFactory(consumer);
    }
}
