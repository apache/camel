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
import org.apache.camel.util.ObjectHelper;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.jboss.netty.handler.ssl.SslHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpServerPipelineFactory extends ServerPipelineFactory {

    private static final Logger LOG = LoggerFactory.getLogger(HttpServerPipelineFactory.class);
    private NettyHttpConsumer consumer;
    private SSLContext sslContext;

    public HttpServerPipelineFactory() {
        // needed for blueprint IoC
    }

    public HttpServerPipelineFactory(NettyHttpConsumer nettyConsumer) {
        this.consumer = nettyConsumer;
        try {
            this.sslContext = createSSLContext();
        } catch (Exception e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }

        LOG.info("Created SslContext {}", sslContext);
    }

    @Override
    public ServerPipelineFactory createPipelineFactory(NettyConsumer nettyConsumer) {
        return new HttpServerPipelineFactory((NettyHttpConsumer) nettyConsumer);
    }

    @Override
    public ChannelPipeline getPipeline() throws Exception {
        // Create a default pipeline implementation.
        ChannelPipeline pipeline = Channels.pipeline();

        if (sslContext != null) {
            SSLEngine engine = sslContext.createSSLEngine();
            engine.setUseClientMode(false);
            engine.setNeedClientAuth(consumer.getConfiguration().isNeedClientAuth());
            pipeline.addLast("ssl", new SslHandler(engine));
        }

        pipeline.addLast("decoder", new HttpRequestDecoder());
        // Uncomment the following line if you don't want to handle HttpChunks.
        pipeline.addLast("aggregator", new HttpChunkAggregator(1048576));
        pipeline.addLast("encoder", new HttpResponseEncoder());
        // Remove the following line if you don't want automatic content compression.
        //pipeline.addLast("deflater", new HttpContentCompressor());

        // handler to route Camel messages
        pipeline.addLast("handler", new HttpServerChannelHandler(consumer));

        return pipeline;
    }

    private SSLContext createSSLContext() throws Exception {
        if (!consumer.getConfiguration().isSsl()) {
            return null;
        }

        if (consumer.getConfiguration().getSslContextParameters() != null) {
            return consumer.getConfiguration().getSslContextParameters().createSSLContext();
        }

        return null;
    }

}
