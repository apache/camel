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

import java.net.URI;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.netty.ChannelHandlerFactory;
import org.apache.camel.component.netty.ClientInitializerFactory;
import org.apache.camel.component.netty.NettyConfiguration;
import org.apache.camel.component.netty.NettyProducer;
import org.apache.camel.component.netty.http.handlers.HttpClientChannelHandler;
import org.apache.camel.component.netty.http.handlers.HttpInboundStreamHandler;
import org.apache.camel.component.netty.http.handlers.HttpOutboundStreamHandler;
import org.apache.camel.component.netty.ssl.SSLEngineFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link ClientInitializerFactory} for the Netty HTTP client.
 */
public class HttpClientInitializerFactory extends ClientInitializerFactory {

    private static final Logger LOG = LoggerFactory.getLogger(HttpClientInitializerFactory.class);
    protected NettyHttpConfiguration configuration;
    private NettyHttpProducer producer;
    private SSLContext sslContext;
    private List<SNIServerName> sniServerNames;

    public HttpClientInitializerFactory() {
        // default constructor needed
    }

    public HttpClientInitializerFactory(NettyHttpProducer nettyProducer) {
        this.producer = nettyProducer;
        try {
            this.sslContext = createSSLContext(producer);
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }

        if (sslContext != null) {
            LOG.info("Created SslContext {}", sslContext);
        }
        configuration = nettyProducer.getConfiguration();
    }

    @Override
    public ClientInitializerFactory createPipelineFactory(NettyProducer nettyProducer) {
        return new HttpClientInitializerFactory((NettyHttpProducer) nettyProducer);
    }

    @Override
    protected void initChannel(Channel ch) throws Exception {
        // create a new pipeline
        ChannelPipeline pipeline = ch.pipeline();

        SslHandler sslHandler = configureClientSSLOnDemand();
        if (sslHandler != null) {
            //TODO must close on SSL exception
            //sslHandler.setCloseOnSSLException(true);
            LOG.debug("Client SSL handler configured and added as an interceptor against the ChannelPipeline: {}", sslHandler);
            pipeline.addLast("ssl", sslHandler);
        }

        pipeline.addLast("http", new HttpClientCodec());

        addToPipeline(producer.getConfiguration().getEncodersAsList(), pipeline, "encoder-");

        addToPipeline(producer.getConfiguration().getDecodersAsList(), pipeline, "decoder-");
        if (configuration.isDisableStreamCache()) {
            pipeline.addLast("inbound-streamer", new HttpInboundStreamHandler());
        }
        pipeline.addLast("aggregator", new HttpObjectAggregator(configuration.getChunkedMaxContentLength()));
        pipeline.addLast("outbound-streamer", new HttpOutboundStreamHandler());

        if (producer.getConfiguration().getRequestTimeout() > 0) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Using request timeout {} millis", producer.getConfiguration().getRequestTimeout());
            }
            ChannelHandler timeout
                    = new ReadTimeoutHandler(producer.getConfiguration().getRequestTimeout(), TimeUnit.MILLISECONDS);
            pipeline.addLast("timeout", timeout);
        }

        // handler to route Camel messages
        pipeline.addLast("handler", new HttpClientChannelHandler(producer));
    }

    private void addToPipeline(List<ChannelHandler> handlers, ChannelPipeline pipeline, String prefix) {
        for (int x = 0; x < handlers.size(); x++) {
            ChannelHandler handler = handlers.get(x);
            if (handler instanceof ChannelHandlerFactory) {
                // use the factory to create a new instance of the channel as it may not be shareable
                handler = ((ChannelHandlerFactory) handler).newChannelHandler();
            }
            pipeline.addLast(prefix + x, handler);
        }
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
            if (answer.getSupportedSSLParameters().getServerNames() != null
                    && !answer.getSupportedSSLParameters().getServerNames().isEmpty()) {
                sniServerNames = answer.getSupportedSSLParameters().getServerNames();
            }
        } else {
            InitializerHelper.logConfiguration(configuration);
            char[] pw = configuration.getPassphrase() != null ? configuration.getPassphrase().toCharArray() : null;

            SSLEngineFactory sslEngineFactory;
            if (configuration.getKeyStoreFile() != null || configuration.getTrustStoreFile() != null) {
                sslEngineFactory = new SSLEngineFactory();
                answer = sslEngineFactory.createSSLContext(producer.getContext(),
                        configuration.getKeyStoreFormat(),
                        configuration.getSecurityProvider(),
                        "file:" + configuration.getKeyStoreFile().getPath(),
                        "file:" + configuration.getTrustStoreFile().getPath(),
                        pw);
            } else if (configuration.getKeyStoreResource() != null || configuration.getTrustStoreResource() != null) {
                sslEngineFactory = new SSLEngineFactory();
                answer = sslEngineFactory.createSSLContext(producer.getContext(),
                        configuration.getKeyStoreFormat(),
                        configuration.getSecurityProvider(),
                        configuration.getKeyStoreResource(),
                        configuration.getTrustStoreResource(),
                        pw);
            } else {
                answer = SSLContext.getDefault();
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
            URI uri = new URI(producer.getEndpoint().getEndpointUri());
            SSLEngine engine = sslContext.createSSLEngine(uri.getHost(), uri.getPort());
            engine.setUseClientMode(true);
            SSLParameters sslParameters = engine.getSSLParameters();
            sslParameters
                    .setServerNames(sniServerNames != null ? sniServerNames : List.of(new SNIHostName(uri.getHost())));
            engine.setSSLParameters(sslParameters);
            if (producer.getConfiguration().getSslContextParameters() == null) {
                // just set the enabledProtocols if the SslContextParameter doesn't set
                engine.setEnabledProtocols(producer.getConfiguration().getEnabledProtocols().split(","));
            }
            return new SslHandler(engine);
        }

        return null;
    }

}
