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

import javax.net.ssl.SSLEngine;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.component.netty.handlers.ClientChannelHandler;
import org.apache.camel.component.netty.ssl.SSLEngineFactory;
import org.jboss.netty.channel.ChannelDownstreamHandler;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelUpstreamHandler;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.ssl.SslHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultClientPipelineFactory extends ClientPipelineFactory {
    private static final transient Logger LOG = LoggerFactory.getLogger(ClientPipelineFactory.class);

    public DefaultClientPipelineFactory(NettyProducer producer, Exchange exchange, AsyncCallback callback) {
        super(producer, exchange, callback);
    }

    public ChannelPipeline getPipeline() throws Exception {
        // create a new pipeline
        ChannelPipeline channelPipeline = Channels.pipeline();

        SslHandler sslHandler = configureClientSSLOnDemand();
        if (sslHandler != null) {
            LOG.debug("Client SSL handler configured and added to the ChannelPipeline");
            channelPipeline.addLast("ssl", sslHandler);
        }

        List<ChannelUpstreamHandler> decoders = producer.getConfiguration().getDecoders();
        for (int x = 0; x < decoders.size(); x++) {
            channelPipeline.addLast("decoder-" + x, decoders.get(x));
        }

        List<ChannelDownstreamHandler> encoders = producer.getConfiguration().getEncoders();
        for (int x = 0; x < encoders.size(); x++) {
            channelPipeline.addLast("encoder-" + x, encoders.get(x));
        }

        // our handler must be added last
        channelPipeline.addLast("handler", new ClientChannelHandler(producer, exchange, callback));

        return channelPipeline;
    }

    private SslHandler configureClientSSLOnDemand() throws Exception {
        if (!producer.getConfiguration().isSsl()) {
            return null;
        }

        if (producer.getConfiguration().getSslHandler() != null) {
            return producer.getConfiguration().getSslHandler();
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

}
