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

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.apache.camel.component.netty.NettyConsumer;
import org.apache.camel.component.netty.ServerPipelineFactory;
import org.apache.camel.component.netty.ssl.SSLEngineFactory;
import org.apache.camel.impl.DefaultClassResolver;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.util.ObjectHelper;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpContentCompressor;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.jboss.netty.handler.ssl.SslHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A shared {@link org.apache.camel.component.netty.ServerPipelineFactory} for a shared Netty HTTP server.
 *
 * @see NettySharedHttpServer
 */
public class HttpServerSharedPipelineFactory extends HttpServerPipelineFactory {

    private static final Logger LOG = LoggerFactory.getLogger(HttpServerSharedPipelineFactory.class);
    private final NettySharedHttpServerBootstrapConfiguration configuration;
    private final HttpServerConsumerChannelFactory channelFactory;
    private final ClassResolver classResolver;
    private SSLContext sslContext;

    public HttpServerSharedPipelineFactory(NettySharedHttpServerBootstrapConfiguration configuration, HttpServerConsumerChannelFactory channelFactory,
                                           ClassResolver classResolver) {
        this.configuration = configuration;
        this.channelFactory = channelFactory;
        // fallback and use default resolver
        this.classResolver = classResolver != null ? classResolver : new DefaultClassResolver();

        try {
            this.sslContext = createSSLContext();
        } catch (Exception e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }

        if (sslContext != null) {
            LOG.info("Created SslContext {}", sslContext);
        }
    }

    @Override
    public ServerPipelineFactory createPipelineFactory(NettyConsumer nettyConsumer) {
        throw new UnsupportedOperationException("Should not call this operation");
    }

    @Override
    public ChannelPipeline getPipeline() throws Exception {
        // Create a default pipeline implementation.
        ChannelPipeline pipeline = Channels.pipeline();

        SslHandler sslHandler = configureServerSSLOnDemand();
        if (sslHandler != null) {
            LOG.debug("Server SSL handler configured and added as an interceptor against the ChannelPipeline: {}", sslHandler);
            pipeline.addLast("ssl", sslHandler);
        }

        pipeline.addLast("decoder", new HttpRequestDecoder(4096, configuration.getMaxHeaderSize(), 8192));
        if (configuration.isChunked()) {
            pipeline.addLast("aggregator", new HttpChunkAggregator(configuration.getChunkedMaxContentLength()));
        }
        pipeline.addLast("encoder", new HttpResponseEncoder());
        if (configuration.isCompression()) {
            pipeline.addLast("deflater", new HttpContentCompressor());
        }

        pipeline.addLast("handler", channelFactory.getChannelHandler());

        return pipeline;
    }

    private SSLContext createSSLContext() throws Exception {
        if (!configuration.isSsl()) {
            return null;
        }

        SSLContext answer;

        // create ssl context once
        if (configuration.getSslContextParameters() != null) {
            answer = configuration.getSslContextParameters().createSSLContext();
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
                answer = sslEngineFactory.createSSLContext(classResolver,
                        configuration.getKeyStoreFormat(),
                        configuration.getSecurityProvider(),
                        "file:" + configuration.getKeyStoreFile().getPath(),
                        "file:" + configuration.getTrustStoreFile().getPath(),
                        configuration.getPassphrase().toCharArray());
            } else {
                sslEngineFactory = new SSLEngineFactory();
                answer = sslEngineFactory.createSSLContext(classResolver,
                        configuration.getKeyStoreFormat(),
                        configuration.getSecurityProvider(),
                        configuration.getKeyStoreResource(),
                        configuration.getTrustStoreResource(),
                        configuration.getPassphrase().toCharArray());
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
