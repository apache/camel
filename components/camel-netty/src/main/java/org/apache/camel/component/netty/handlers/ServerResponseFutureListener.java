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
package org.apache.camel.component.netty.handlers;

import java.net.SocketAddress;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.component.netty.NettyConstants;
import org.apache.camel.component.netty.NettyConsumer;
import org.apache.camel.component.netty.NettyHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link io.netty.channel.ChannelFutureListener} that performs the disconnect logic when sending the response is
 * complete.
 */
public class ServerResponseFutureListener implements ChannelFutureListener {

    private static final Logger LOG = LoggerFactory.getLogger(ServerResponseFutureListener.class);
    private final NettyConsumer consumer;
    private final Exchange exchange;
    private final SocketAddress remoteAddress;

    public ServerResponseFutureListener(NettyConsumer consumer, Exchange exchange, SocketAddress remoteAddress) {
        this.consumer = consumer;
        this.exchange = exchange;
        this.remoteAddress = remoteAddress;
    }

    @Override
    public void operationComplete(ChannelFuture future) throws Exception {
        // if it was not a success then thrown an exception
        if (!future.isSuccess()) {
            Exception e = new CamelExchangeException("Cannot write response to " + remoteAddress, exchange, future.cause());
            consumer.getExceptionHandler().handleException(e);
        }

        // should channel be closed after complete?
        Boolean close;
        if (exchange.hasOut()) {
            close = exchange.getOut().getHeader(NettyConstants.NETTY_CLOSE_CHANNEL_WHEN_COMPLETE, Boolean.class);
        } else {
            close = exchange.getIn().getHeader(NettyConstants.NETTY_CLOSE_CHANNEL_WHEN_COMPLETE, Boolean.class);
        }

        // check the setting on the exchange property
        if (close == null) {
            close = exchange.getProperty(NettyConstants.NETTY_CLOSE_CHANNEL_WHEN_COMPLETE, Boolean.class);
        }

        // should we disconnect, the header can override the configuration
        boolean disconnect = consumer.getConfiguration().isDisconnect();
        if (close != null) {
            disconnect = close;
        }
        if (disconnect) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Closing channel when complete at address: {}", remoteAddress);
            }
            NettyHelper.close(future.channel());
        }
    }
}
