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
package org.apache.camel.component.netty4.http;


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
import org.apache.camel.component.netty4.ChannelHandlerFactory;
import org.apache.camel.component.netty4.NettyConsumer;
import org.apache.camel.component.netty4.NettyServerBootstrapConfiguration;
import org.apache.camel.component.netty4.ServerInitializerFactory;
import org.apache.camel.component.netty4.ssl.SSLEngineFactory;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
            throw ObjectHelper.wrapRuntimeCamelException(e);
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
        
        SslHandler sslHandler = configureServerSSLOnDemand();
        if (sslHandler != null) {
            //TODO must close on SSL exception
            // sslHandler.setCloseOnSSLException(true);
            LOG.debug("Server SSL handler configured and added as an interceptor against the ChannelPipeline: {}", sslHandler);
            pipeline.addLast("ssl", sslHandler);
        }

        pipeline.addLast("decoder", new HttpRequestDecoder(4096, configuration.getMaxHeaderSize(), 8192));
        List<ChannelHandler> decoders = consumer.getConfiguration().getDecoders();
        for (int x = 0; x < decoders.size(); x++) {
            ChannelHandler decoder = decoders.get(x);
            if (decoder instanceof ChannelHandlerFactory) {
                // use the factory to create a new instance of the channel as it may not be shareable
                decoder = ((ChannelHandlerFactory) decoder).newChannelHandler();
            }
            pipeline.addLast("decoder-" + x, decoder);
        }
        pipeline.addLast("encoder", new HttpResponseEncoder());
        List<ChannelHandler> encoders = consumer.getConfiguration().getEncoders();
        for (int x = 0; x < encoders.size(); x++) {
            ChannelHandler encoder = encoders.get(x);
            if (encoder instanceof ChannelHandlerFactory) {
                // use the factory to create a new instance of the channel as it may not be shareable
                encoder = ((ChannelHandlerFactory) encoder).newChannelHandler();
            }
            pipeline.addLast("encoder-" + x, encoder);
        }
        pipeline.addLast("aggregator", new HttpObjectAggregator(configuration.getChunkedMaxContentLength()));
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

    private SSLContext createSSLContext(CamelContext camelContext, NettyServerBootstrapConfiguration configuration) throws Exception {
        if (!configuration.isSsl()) {
            return null;
        }

        SSLContext answer;

        // create ssl context once
        if (configuration.getSslContextParameters() != null) {
            answer = configuration.getSslContextParameters().createSSLContext(camelContext);
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
                answer = sslEngineFactory.createSSLContext(camelContext.getClassResolver(),
                        configuration.getKeyStoreFormat(),
                        configuration.getSecurityProvider(),
                        "file:" + configuration.getKeyStoreFile().getPath(),
                        "file:" + configuration.getTrustStoreFile().getPath(),
                        configuration.getPassphrase().toCharArray());
            } else {
                sslEngineFactory = new SSLEngineFactory();
                answer = sslEngineFactory.createSSLContext(camelContext.getClassResolver(),
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
