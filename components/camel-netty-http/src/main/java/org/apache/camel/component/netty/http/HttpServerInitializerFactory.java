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

import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.concurrent.EventExecutorGroup;
import org.apache.camel.CamelContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.netty.ChannelHandlerFactory;
import org.apache.camel.component.netty.NettyConsumer;
import org.apache.camel.component.netty.NettyServerBootstrapConfiguration;
import org.apache.camel.component.netty.ServerInitializerFactory;
import org.apache.camel.component.netty.http.handlers.HttpInboundStreamHandler;
import org.apache.camel.component.netty.http.handlers.HttpOutboundStreamHandler;
import org.apache.camel.component.netty.ssl.SSLEngineFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.netty.http.InitializerHelper.logConfiguration;

/**
 * {@link ServerInitializerFactory} for the Netty HTTP server.
 */
public class HttpServerInitializerFactory extends ServerInitializerFactory {

    private static final Logger LOG = LoggerFactory.getLogger(HttpServerInitializerFactory.class);
    protected NettyHttpConsumer consumer;
    protected SSLContext sslContext;
    protected NettyHttpConfiguration configuration;

    public HttpServerInitializerFactory() {
        // default constructor needed
    }

    public HttpServerInitializerFactory(NettyHttpConsumer nettyConsumer) {
        this.consumer = nettyConsumer;
        this.configuration = nettyConsumer.getConfiguration();
        try {
            this.sslContext = createSSLContext(consumer.getContext(), consumer.getConfiguration());
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }

        if (sslContext != null) {
            LOG.info("Created SslContext {}", sslContext);
        }
    }

    @Override
    public ServerInitializerFactory createPipelineFactory(NettyConsumer nettyConsumer) {
        return new HttpServerInitializerFactory((NettyHttpConsumer) nettyConsumer);
    }

    @Override
    protected void initChannel(Channel ch) throws Exception {
        // create a new pipeline
        ChannelPipeline pipeline = ch.pipeline();

        ChannelHandler sslHandler = configureServerSSLOnDemand();
        if (sslHandler != null) {
            //TODO must close on SSL exception
            // sslHandler.setCloseOnSSLException(true);

            if (sslHandler instanceof ChannelHandlerFactory) {
                // use the factory to create a new instance of the channel as it may not be shareable
                sslHandler = ((ChannelHandlerFactory) sslHandler).newChannelHandler();
            }

            LOG.debug("Server SSL handler configured and added as an interceptor against the ChannelPipeline: {}", sslHandler);
            pipeline.addLast("ssl", sslHandler);
        }

        pipeline.addLast("decoder", new HttpRequestDecoder(
                configuration.getMaxInitialLineLength(), configuration.getMaxHeaderSize(), configuration.getMaxChunkSize()));
        List<ChannelHandler> decoders = consumer.getConfiguration().getDecodersAsList();
        for (int x = 0; x < decoders.size(); x++) {
            ChannelHandler decoder = decoders.get(x);
            if (decoder instanceof ChannelHandlerFactory) {
                // use the factory to create a new instance of the channel as it may not be shareable
                decoder = ((ChannelHandlerFactory) decoder).newChannelHandler();
            }
            pipeline.addLast("decoder-" + x, decoder);
        }
        pipeline.addLast("encoder", new HttpResponseEncoder());
        List<ChannelHandler> encoders = consumer.getConfiguration().getEncodersAsList();
        for (int x = 0; x < encoders.size(); x++) {
            ChannelHandler encoder = encoders.get(x);
            if (encoder instanceof ChannelHandlerFactory) {
                // use the factory to create a new instance of the channel as it may not be shareable
                encoder = ((ChannelHandlerFactory) encoder).newChannelHandler();
            }
            pipeline.addLast("encoder-" + x, encoder);
        }
        if (configuration.isDisableStreamCache()) {
            pipeline.addLast("inbound-streamer", new HttpInboundStreamHandler());
        }
        pipeline.addLast("aggregator", new HttpObjectAggregator(configuration.getChunkedMaxContentLength()));
        pipeline.addLast("outbound-streamer", new HttpOutboundStreamHandler());
        if (supportCompressed()) {
            pipeline.addLast("deflater", new HttpContentCompressor());
        }

        int port = consumer.getConfiguration().getPort();
        ChannelHandler handler = consumer.getEndpoint().getComponent().getMultiplexChannelHandler(port).getChannelHandler();

        if (consumer.getConfiguration().isUsingExecutorService()) {
            EventExecutorGroup applicationExecutor = consumer.getEndpoint().getComponent().getExecutorService();
            pipeline.addLast(applicationExecutor, "handler", handler);
        } else {
            pipeline.addLast("handler", handler);
        }
    }

    private SSLContext createSSLContext(CamelContext camelContext, NettyServerBootstrapConfiguration configuration)
            throws Exception {
        if (!configuration.isSsl()) {
            return null;
        }

        SSLContext answer;

        // create ssl context once
        if (configuration.getSslContextParameters() != null) {
            answer = configuration.getSslContextParameters().createSSLContext(camelContext);
        } else {
            logConfiguration(configuration);
            char[] pw = configuration.getPassphrase() != null ? configuration.getPassphrase().toCharArray() : null;

            SSLEngineFactory sslEngineFactory;
            if (configuration.getKeyStoreFile() != null || configuration.getTrustStoreFile() != null) {
                sslEngineFactory = new SSLEngineFactory();
                answer = sslEngineFactory.createSSLContext(camelContext,
                        configuration.getKeyStoreFormat(),
                        configuration.getSecurityProvider(),
                        "file:" + configuration.getKeyStoreFile().getPath(),
                        "file:" + configuration.getTrustStoreFile().getPath(),
                        pw);
            } else {
                sslEngineFactory = new SSLEngineFactory();
                answer = sslEngineFactory.createSSLContext(camelContext,
                        configuration.getKeyStoreFormat(),
                        configuration.getSecurityProvider(),
                        configuration.getKeyStoreResource(),
                        configuration.getTrustStoreResource(),
                        pw);
            }
        }

        return answer;
    }

    private SslHandler configureServerSSLOnDemand() {
        if (!consumer.getConfiguration().isSsl()) {
            return null;
        }

        if (consumer.getConfiguration().getSslHandler() != null) {
            return consumer.getConfiguration().getSslHandler();
        } else if (sslContext != null) {
            SSLEngine engine = sslContext.createSSLEngine();
            engine.setUseClientMode(false);
            engine.setNeedClientAuth(consumer.getConfiguration().isNeedClientAuth());
            if (consumer.getConfiguration().getSslContextParameters() == null) {
                // just set the enabledProtocols if the SslContextParameter doesn't set
                engine.setEnabledProtocols(consumer.getConfiguration().getEnabledProtocols().split(","));
            }
            return new SslHandler(engine);
        }

        return null;
    }

    private boolean supportCompressed() {
        return consumer.getEndpoint().getConfiguration().isCompression();
    }

}
