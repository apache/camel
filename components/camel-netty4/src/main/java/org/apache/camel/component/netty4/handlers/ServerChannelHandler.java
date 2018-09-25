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
package org.apache.camel.component.netty4.handlers;

import java.net.SocketAddress;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.component.netty4.NettyConstants;
import org.apache.camel.component.netty4.NettyConsumer;
import org.apache.camel.component.netty4.NettyHelper;
import org.apache.camel.component.netty4.NettyPayloadHelper;
import org.apache.camel.util.CamelLogger;
import org.apache.camel.util.IOHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server handler which cannot be shared
 */
public class ServerChannelHandler extends SimpleChannelInboundHandler<Object> {
    // use NettyConsumer as logger to make it easier to read the logs as this is part of the consumer
    private static final Logger LOG = LoggerFactory.getLogger(NettyConsumer.class);
    private final NettyConsumer consumer;
    private final CamelLogger noReplyLogger;

    public ServerChannelHandler(NettyConsumer consumer) {
        this.consumer = consumer;
        this.noReplyLogger = new CamelLogger(LOG, consumer.getConfiguration().getNoReplyLogLevel());
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Channel open: {}", ctx.channel());
        }
        // to keep track of open sockets
        consumer.getNettyServerBootstrapFactory().addChannel(ctx.channel());
        
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Channel closed: {}", ctx.channel());
        }
        // to keep track of open sockets
        consumer.getNettyServerBootstrapFactory().removeChannel(ctx.channel());
        
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        // only close if we are still allowed to run
        if (consumer.isRunAllowed()) {
            // let the exception handler deal with it
            consumer.getExceptionHandler().handleException("Closing channel as an exception was thrown from Netty", cause);
            // close channel in case an exception was thrown
            NettyHelper.close(ctx.channel());
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        Object in = msg;
        if (LOG.isDebugEnabled()) {
            LOG.debug("Channel: {} received body: {}", ctx.channel(), in);
        }

        // create Exchange and let the consumer process it
        final Exchange exchange = consumer.getEndpoint().createExchange(ctx, msg);
        if (consumer.getConfiguration().isSync()) {
            exchange.setPattern(ExchangePattern.InOut);
        }
        // set the exchange charset property for converting
        if (consumer.getConfiguration().getCharsetName() != null) {
            exchange.setProperty(Exchange.CHARSET_NAME, IOHelper.normalizeCharset(consumer.getConfiguration().getCharsetName()));
        }
        if (consumer.getConfiguration().isReuseChannel()) {
            exchange.setProperty(NettyConstants.NETTY_CHANNEL, ctx.channel());
        }

        // we want to handle the UoW
        consumer.createUoW(exchange);

        beforeProcess(exchange, ctx, msg);

        // process accordingly to endpoint configuration
        if (consumer.getEndpoint().isSynchronous()) {
            processSynchronously(exchange, ctx, msg);
        } else {
            processAsynchronously(exchange, ctx, msg);
        }
    }

    /**
     * Allows any custom logic before the {@link Exchange} is processed by the routing engine.
     *
     * @param exchange       the exchange
     * @param ctx            the channel handler context
     * @param message        the message which needs to be sent
     */
    protected void beforeProcess(final Exchange exchange, final ChannelHandlerContext ctx, final Object message) {
        // noop
    }

    private void processSynchronously(final Exchange exchange, final ChannelHandlerContext ctx, final Object message) {
        try {
            consumer.getProcessor().process(exchange);
            if (consumer.getConfiguration().isSync()) {
                sendResponse(message, ctx, exchange);
            }
        } catch (Throwable e) {
            consumer.getExceptionHandler().handleException(e);
        } finally {
            consumer.doneUoW(exchange);
        }
    }

    private void processAsynchronously(final Exchange exchange, final ChannelHandlerContext ctx, final Object message) {
        consumer.getAsyncProcessor().process(exchange, new AsyncCallback() {
            @Override
            public void done(boolean doneSync) {
                // send back response if the communication is synchronous
                try {
                    if (consumer.getConfiguration().isSync()) {
                        sendResponse(message, ctx, exchange);
                    }
                } catch (Throwable e) {
                    consumer.getExceptionHandler().handleException(e);
                } finally {
                    consumer.doneUoW(exchange);
                }
            }
        });
    }

    private void sendResponse(Object message, ChannelHandlerContext ctx, Exchange exchange) throws Exception {
        Object body = getResponseBody(exchange);

        if (body == null) {
            noReplyLogger.log("No payload to send as reply for exchange: " + exchange);
            if (consumer.getConfiguration().isDisconnectOnNoReply()) {
                // must close session if no data to write otherwise client will never receive a response
                // and wait forever (if not timing out)
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Closing channel as no payload to send as reply at address: {}", ctx.channel().remoteAddress());
                }
                NettyHelper.close(ctx.channel());
            }
        } else {
            // if textline enabled then covert to a String which must be used for textline
            if (consumer.getConfiguration().isTextline()) {
                body = NettyHelper.getTextlineBody(body, exchange, consumer.getConfiguration().getDelimiter(), consumer.getConfiguration().isAutoAppendDelimiter());
            }

            // we got a body to write
            ChannelFutureListener listener = createResponseFutureListener(consumer, exchange, ctx.channel().remoteAddress());
            if (consumer.getConfiguration().isTcp()) {
                NettyHelper.writeBodyAsync(LOG, ctx.channel(), null, body, exchange, listener);
            } else {
                NettyHelper.writeBodyAsync(LOG, ctx.channel(), exchange.getProperty(NettyConstants.NETTY_REMOTE_ADDRESS, SocketAddress.class), body, exchange, listener);
            }
        }
    }

    /**
     * Gets the object we want to use as the response object for sending to netty.
     *
     * @param exchange the exchange
     * @return the object to use as response
     * @throws Exception is thrown if error getting the response body
     */
    protected Object getResponseBody(Exchange exchange) throws Exception {
        // if there was an exception then use that as response body
        boolean exception = exchange.getException() != null && !consumer.getEndpoint().getConfiguration().isTransferExchange();
        if (exception) {
            return exchange.getException();
        }
        if (exchange.hasOut()) {
            return NettyPayloadHelper.getOut(consumer.getEndpoint(), exchange);
        } else {
            return NettyPayloadHelper.getIn(consumer.getEndpoint(), exchange);
        }
    }

    /**
     * Creates the {@link ChannelFutureListener} to execute when writing the response is complete.
     *
     * @param consumer          the netty consumer
     * @param exchange          the exchange
     * @param remoteAddress     the remote address of the message
     * @return the listener.
     */
    protected ChannelFutureListener createResponseFutureListener(NettyConsumer consumer, Exchange exchange, SocketAddress remoteAddress) {
        return new ServerResponseFutureListener(consumer, exchange, remoteAddress);
    }

}
