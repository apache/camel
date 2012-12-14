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

import org.apache.camel.component.netty.handlers.ClientChannelHandler;
import org.apache.camel.component.netty.ssl.SSLEngineFactory;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.ssl.SslHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultClientPipelineFactory extends ClientPipelineFactory  {
    private static final transient Logger LOG = LoggerFactory.getLogger(DefaultClientPipelineFactory.class);

    private final NettyProducer producer;

    public DefaultClientPipelineFactory(NettyProducer producer) {
        this.producer = producer;
    }

    public ChannelPipeline getPipeline() throws Exception {
        // create a new pipeline
        ChannelPipeline channelPipeline = Channels.pipeline();

        SslHandler sslHandler = configureClientSSLOnDemand(producer);
        if (sslHandler != null) {
            LOG.debug("Client SSL handler configured and added to the ChannelPipeline");
            addToPipeline("ssl", channelPipeline, sslHandler);
        }

        List<ChannelHandler> decoders = producer.getConfiguration().getDecoders();
        for (int x = 0; x < decoders.size(); x++) {
            ChannelHandler decoder = decoders.get(x);
            if (decoder instanceof ChannelHandlerFactory) {
                // use the factory to create a new instance of the channel as it may not be shareable
                decoder = ((ChannelHandlerFactory) decoder).newChannelHandler();
            }
            addToPipeline("decoder-" + x, channelPipeline, decoder);
        }

        List<ChannelHandler> encoders = producer.getConfiguration().getEncoders();
        for (int x = 0; x < encoders.size(); x++) {
            ChannelHandler encoder = encoders.get(x);
            if (encoder instanceof ChannelHandlerFactory) {
                // use the factory to create a new instance of the channel as it may not be shareable
                encoder = ((ChannelHandlerFactory) encoder).newChannelHandler();
            }
            addToPipeline("encoder-" + x, channelPipeline, encoder);
        }

        // our handler must be added last
        addToPipeline("handler", channelPipeline, new ClientChannelHandler(producer));

        LOG.trace("Created ChannelPipeline: {}", channelPipeline);
        return channelPipeline;
    }

    private void addToPipeline(String name, ChannelPipeline pipeline, ChannelHandler handler) {
        pipeline.addLast(name, handler);
    }

    private SslHandler configureClientSSLOnDemand(NettyProducer producer) throws Exception {
        if (!producer.getConfiguration().isSsl()) {
            return null;
        }

        if (producer.getConfiguration().getSslHandler() != null) {
            return producer.getConfiguration().getSslHandler();
        } else if (producer.getConfiguration().getSslContextParameters() != null) {
            SSLContext context = producer.getConfiguration().getSslContextParameters().createSSLContext();
            SSLEngine engine = context.createSSLEngine();
            engine.setUseClientMode(true);
            return new SslHandler(engine);
        } else {
            if (producer.getConfiguration().getKeyStoreFile() == null) {
                LOG.debug("keystorefile is null");
            }
            if (producer.getConfiguration().getTrustStoreFile() == null) {
                LOG.debug("truststorefile is null");
            }
            if (producer.getConfiguration().getPassphrase().toCharArray() == null) {
                LOG.debug("passphrase is null");
            }
            SSLEngineFactory sslEngineFactory = new SSLEngineFactory(
                producer.getConfiguration().getKeyStoreFormat(),
                producer.getConfiguration().getSecurityProvider(),
                producer.getConfiguration().getKeyStoreFile(),
                producer.getConfiguration().getTrustStoreFile(),
                producer.getConfiguration().getPassphrase().toCharArray());
            SSLEngine sslEngine = sslEngineFactory.createClientSSLEngine();
            return new SslHandler(sslEngine);
        }
    }

    @Override
    public ClientPipelineFactory createPipelineFactory(NettyProducer producer) {
        return new DefaultClientPipelineFactory(producer);
    }
}
