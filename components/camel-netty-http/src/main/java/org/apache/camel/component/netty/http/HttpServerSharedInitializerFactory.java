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

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.ssl.SslHandler;
import org.apache.camel.CamelContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.netty.NettyConsumer;
import org.apache.camel.component.netty.ServerInitializerFactory;
import org.apache.camel.component.netty.http.handlers.HttpInboundStreamHandler;
import org.apache.camel.component.netty.http.handlers.HttpOutboundStreamHandler;
import org.apache.camel.component.netty.ssl.SSLEngineFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A shared {@link HttpServerInitializerFactory} for a shared Netty HTTP server.
 *
 * @see NettySharedHttpServer
 */
public class HttpServerSharedInitializerFactory extends HttpServerInitializerFactory {

    private static final Logger LOG = LoggerFactory.getLogger(HttpServerSharedInitializerFactory.class);
    private final NettySharedHttpServerBootstrapConfiguration configuration;
    private final HttpServerConsumerChannelFactory channelFactory;
    private final CamelContext camelContext;
    private SSLContext sslContext;

    public HttpServerSharedInitializerFactory(NettySharedHttpServerBootstrapConfiguration configuration, HttpServerConsumerChannelFactory channelFactory,
                                              CamelContext camelContext) {
        this.configuration = configuration;
        this.channelFactory = channelFactory;
        // fallback and use default resolver
        this.camelContext = camelContext;

        try {
            this.sslContext = createSSLContext();
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }

        if (sslContext != null) {
            LOG.info("Created SslContext {}", sslContext);
        }
    }

    @Override
    public ServerInitializerFactory createPipelineFactory(NettyConsumer nettyConsumer) {
        throw new UnsupportedOperationException("Should not call this operation");
    }

    @Override
    protected void initChannel(Channel ch) throws Exception {
        // create a new pipeline
        ChannelPipeline pipeline = ch.pipeline();

        SslHandler sslHandler = configureServerSSLOnDemand();
        if (sslHandler != null) {
            LOG.debug("Server SSL handler configured and added as an interceptor against the ChannelPipeline: {}", sslHandler);
            pipeline.addLast("ssl", sslHandler);
        }

        pipeline.addLast("decoder", new HttpRequestDecoder(4096, configuration.getMaxHeaderSize(), 8192));
        pipeline.addLast("encoder", new HttpResponseEncoder());
        if (configuration.isChunked()) {
            pipeline.addLast("inbound-streamer", new HttpInboundStreamHandler());
            pipeline.addLast("aggregator", new HttpObjectAggregator(configuration.getChunkedMaxContentLength()));
            pipeline.addLast("outbound-streamer", new HttpOutboundStreamHandler());
        }
        if (configuration.isCompression()) {
            pipeline.addLast("deflater", new HttpContentCompressor());
        }

        pipeline.addLast("handler", channelFactory.getChannelHandler());
    }

    private SSLContext createSSLContext() throws Exception {
        if (!configuration.isSsl()) {
            return null;
        }

        SSLContext answer;

        // create ssl context once
        if (configuration.getSslContextParameters() != null) {
            answer = configuration.getSslContextParameters().createSSLContext(null);
        } else {
            if (configuration.getKeyStoreFile() == null && configuration.getKeyStoreResource() == null) {
                LOG.debug("keystorefile is null");
            }
            if (configuration.getTrustStoreFile() == null && configuration.getTrustStoreResource() == null) {
                LOG.debug("truststorefile is null");
            }
            if (configuration.getPassphrase() == null) {
                LOG.debug("passphrase is null");
            }
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

    private SslHandler configureServerSSLOnDemand() throws Exception {
        if (!configuration.isSsl()) {
            return null;
        }

        if (configuration.getSslHandler() != null) {
            return configuration.getSslHandler();
        } else if (sslContext != null) {
            SSLEngine engine = sslContext.createSSLEngine();
            engine.setUseClientMode(false);
            engine.setNeedClientAuth(configuration.isNeedClientAuth());
            if (configuration.getSslContextParameters() == null) {
                // just set the enabledProtocols if the SslContextParameter doesn't set
                engine.setEnabledProtocols(configuration.getEnabledProtocols().split(","));
            }
            return new SslHandler(engine);
        }

        return null;
    }

}
