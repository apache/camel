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
package org.apache.camel.component.netty4;

import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.ReadTimeoutHandler;

import org.apache.camel.component.netty4.handlers.ClientChannelHandler;
import org.apache.camel.component.netty4.ssl.SSLEngineFactory;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultClientInitializerFactory extends ClientInitializerFactory  {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultClientInitializerFactory.class);

    private final NettyProducer producer;
    private SSLContext sslContext;

    public DefaultClientInitializerFactory(NettyProducer producer) {
        this.producer = producer;
        try {
            this.sslContext = createSSLContext(producer);
        } catch (Exception e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
    }

    protected void initChannel(Channel ch) throws Exception {
        // create a new pipeline
        ChannelPipeline channelPipeline = ch.pipeline();

        SslHandler sslHandler = configureClientSSLOnDemand();
        if (sslHandler != null) {
            //TODO  must close on SSL exception
            //sslHandler.setCloseOnSSLException(true);
            LOG.debug("Client SSL handler configured and added to the ChannelPipeline: {}", sslHandler);
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

        // do we use request timeout?
        if (producer.getConfiguration().getRequestTimeout() > 0) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Using request timeout {} millis", producer.getConfiguration().getRequestTimeout());
            }
            ChannelHandler timeout = new ReadTimeoutHandler(producer.getConfiguration().getRequestTimeout(), TimeUnit.MILLISECONDS);
            addToPipeline("timeout", channelPipeline, timeout);
        }

        // our handler must be added last
        addToPipeline("handler", channelPipeline, new ClientChannelHandler(producer));

        LOG.trace("Created ChannelPipeline: {}", channelPipeline);
        
    }

    private void addToPipeline(String name, ChannelPipeline pipeline, ChannelHandler handler) {
        pipeline.addLast(name, handler);
    }

    private SSLContext createSSLContext(NettyProducer producer) throws Exception {
        NettyConfiguration configuration = producer.getConfiguration();

        if (!configuration.isSsl()) {
            return null;
        }

        SSLContext answer;

        // create ssl context once
        if (configuration.getSslContextParameters() != null) {
            answer = configuration.getSslContextParameters().createSSLContext(producer.getContext());
        } else {
            if (configuration.getKeyStoreFile() == null && configuration.getKeyStoreResource() == null) {
                LOG.debug("keystorefile is null");
            }
            if (configuration.getTrustStoreFile() == null && configuration.getTrustStoreResource() == null) {
                LOG.debug("truststorefile is null");
            }
            if (configuration.getPassphrase().toCharArray() == null) {
                LOG.debug("passphrase is null");
            }

            SSLEngineFactory sslEngineFactory;
            if (configuration.getKeyStoreFile() != null || configuration.getTrustStoreFile() != null) {
                sslEngineFactory = new SSLEngineFactory();
                answer = sslEngineFactory.createSSLContext(producer.getContext().getClassResolver(),
                        configuration.getKeyStoreFormat(),
                        configuration.getSecurityProvider(),
                        "file:" + configuration.getKeyStoreFile().getPath(),
                        "file:" + configuration.getTrustStoreFile().getPath(),
                        configuration.getPassphrase().toCharArray());
            } else {
                sslEngineFactory = new SSLEngineFactory();
                answer = sslEngineFactory.createSSLContext(producer.getContext().getClassResolver(),
                        configuration.getKeyStoreFormat(),
                        configuration.getSecurityProvider(),
                        configuration.getKeyStoreResource(),
                        configuration.getTrustStoreResource(),
                        configuration.getPassphrase().toCharArray());
            }
        }

        return answer;
    }

    private SslHandler configureClientSSLOnDemand() throws Exception {
        if (!producer.getConfiguration().isSsl()) {
            return null;
        }

        if (producer.getConfiguration().getSslHandler() != null) {
            return producer.getConfiguration().getSslHandler();
        } else if (sslContext != null) {
            SSLEngine engine = sslContext.createSSLEngine();
            engine.setUseClientMode(true);
            if (producer.getConfiguration().getSslContextParameters() == null) {
                // just set the enabledProtocols if the SslContextParameter doesn't set
                engine.setEnabledProtocols(producer.getConfiguration().getEnabledProtocols().split(","));
            }
            return new SslHandler(engine);
        }

        return null;
    }

    @Override
    public ClientInitializerFactory createPipelineFactory(NettyProducer producer) {
        return new DefaultClientInitializerFactory(producer);
    }
}
