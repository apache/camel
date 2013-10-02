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

import org.apache.camel.CamelContext;
import org.apache.camel.component.netty.NettyConsumer;
import org.apache.camel.component.netty.NettyServerBootstrapConfiguration;
import org.apache.camel.component.netty.ServerPipelineFactory;
import org.apache.camel.component.netty.ssl.SSLEngineFactory;
import org.apache.camel.util.ObjectHelper;
import org.jboss.netty.channel.ChannelHandler;
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
 * {@link ServerPipelineFactory} for the Netty HTTP server.
 */
public class HttpServerPipelineFactory extends ServerPipelineFactory {

    private static final Logger LOG = LoggerFactory.getLogger(HttpServerPipelineFactory.class);
    protected NettyHttpConsumer consumer;
    protected SSLContext sslContext;
    protected NettyServerBootstrapConfiguration configuration;

    public HttpServerPipelineFactory() {
        // default constructor needed
    }

    public HttpServerPipelineFactory(NettyHttpConsumer nettyConsumer) {
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
    public ServerPipelineFactory createPipelineFactory(NettyConsumer nettyConsumer) {
        return new HttpServerPipelineFactory((NettyHttpConsumer) nettyConsumer);
    }

    @Override
    public ChannelPipeline getPipeline() throws Exception {
        // Create a default pipeline implementation.
        ChannelPipeline pipeline = Channels.pipeline();

        SslHandler sslHandler = configureServerSSLOnDemand();
        if (sslHandler != null) {
            // must close on SSL exception
            sslHandler.setCloseOnSSLException(true);
            LOG.debug("Server SSL handler configured and added as an interceptor against the ChannelPipeline: {}", sslHandler);
            pipeline.addLast("ssl", sslHandler);
        }

        pipeline.addLast("decoder", new HttpRequestDecoder());
        pipeline.addLast("aggregator", new HttpChunkAggregator(1048576));

        pipeline.addLast("encoder", new HttpResponseEncoder());
        if (supportCompressed()) {
            pipeline.addLast("deflater", new HttpContentCompressor());
        }

        int port = consumer.getConfiguration().getPort();
        ChannelHandler handler = consumer.getEndpoint().getComponent().getMultiplexChannelHandler(port).getChannelHandler();
        pipeline.addLast("handler", handler);

        return pipeline;
    }

    private SSLContext createSSLContext(CamelContext camelContext, NettyServerBootstrapConfiguration configuration) throws Exception {
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
            return new SslHandler(engine);
        }

        return null;
    }

    private boolean supportCompressed() {
        return consumer.getEndpoint().getConfiguration().isCompression();
    }

}
