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
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.apache.camel.component.netty.handlers.ServerChannelHandler;
import org.apache.camel.component.netty.ssl.SSLEngineFactory;
import org.apache.camel.util.ObjectHelper;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.execution.ExecutionHandler;
import org.jboss.netty.handler.ssl.SslHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultServerPipelineFactory extends ServerPipelineFactory {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultServerPipelineFactory.class);

    private final NettyConsumer consumer;
    private SSLContext sslContext;

    public DefaultServerPipelineFactory(NettyConsumer consumer) {
        this.consumer = consumer;
        try {
            this.sslContext = createSSLContext(consumer);
        } catch (Exception e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
    }

    @Override
    public ChannelPipeline getPipeline() throws Exception {
        ChannelPipeline channelPipeline = Channels.pipeline();

        SslHandler sslHandler = configureServerSSLOnDemand();
        if (sslHandler != null) {
            LOG.debug("Server SSL handler configured and added as an interceptor against the ChannelPipeline: {}", sslHandler);
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
            // this must be added just before the ServerChannelHandler
            // use ordered thread pool, to ensure we process the events in order, and can send back
            // replies in the expected order. eg this is required by TCP.
            // and use a Camel thread factory so we have consistent thread namings
            ExecutionHandler executionHandler = new ExecutionHandler(consumer.getEndpoint().getComponent().getExecutorService());
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

    private SSLContext createSSLContext(NettyConsumer consumer) throws Exception {
        if (!consumer.getConfiguration().isSsl()) {
            return null;
        }

        // create ssl context once
        if (consumer.getConfiguration().getSslContextParameters() != null) {
            SSLContext context = consumer.getConfiguration().getSslContextParameters().createSSLContext();
            return context;
        }

        return null;
    }

    private SslHandler configureServerSSLOnDemand() throws Exception {
        if (!consumer.getConfiguration().isSsl()) {
            return null;
        }

        if (consumer.getConfiguration().getSslHandler() != null) {
            return consumer.getConfiguration().getSslHandler();
        } else if (sslContext != null) {
            SSLEngine engine = sslContext.createSSLEngine();
            engine.setUseClientMode(false);
            engine.setNeedClientAuth(consumer.getConfiguration().isNeedClientAuth());
            return new SslHandler(engine);
        } else {
            if (consumer.getConfiguration().getKeyStoreFile() == null && consumer.getConfiguration().getKeyStoreResource() == null) {
                LOG.debug("keystorefile is null");
            }
            if (consumer.getConfiguration().getTrustStoreFile() == null && consumer.getConfiguration().getTrustStoreResource() == null) {
                LOG.debug("truststorefile is null");
            }
            if (consumer.getConfiguration().getPassphrase().toCharArray() == null) {
                LOG.debug("passphrase is null");
            }
            SSLEngineFactory sslEngineFactory;
            if (consumer.getConfiguration().getKeyStoreFile() != null || consumer.getConfiguration().getTrustStoreFile() != null) {
                sslEngineFactory = new SSLEngineFactory(
                        consumer.getConfiguration().getKeyStoreFormat(),
                        consumer.getConfiguration().getSecurityProvider(),
                        consumer.getConfiguration().getKeyStoreFile(),
                        consumer.getConfiguration().getTrustStoreFile(),
                        consumer.getConfiguration().getPassphrase().toCharArray());
            } else {
                sslEngineFactory = new SSLEngineFactory(consumer.getContext().getClassResolver(),
                        consumer.getConfiguration().getKeyStoreFormat(),
                        consumer.getConfiguration().getSecurityProvider(),
                        consumer.getConfiguration().getKeyStoreResource(),
                        consumer.getConfiguration().getTrustStoreResource(),
                        consumer.getConfiguration().getPassphrase().toCharArray());
            }
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
