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
package org.apache.camel.component.netty.handlers;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.component.netty.NettyConstants;
import org.apache.camel.component.netty.NettyConsumer;
import org.apache.camel.component.netty.NettyHelper;
import org.apache.camel.component.netty.NettyPayloadHelper;
import org.apache.camel.util.CamelLogger;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.IOHelper;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client handler which cannot be shared
 */
public class ServerChannelHandler extends SimpleChannelUpstreamHandler {
    private static final transient Logger LOG = LoggerFactory.getLogger(ServerChannelHandler.class);
    private NettyConsumer consumer;
    private CamelLogger noReplyLogger;

    public ServerChannelHandler(NettyConsumer consumer) {
        this.consumer = consumer;    
        this.noReplyLogger = new CamelLogger(LOG, consumer.getConfiguration().getNoReplyLogLevel());
    }

    @Override
    public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        LOG.trace("Channel open: {}", e.getChannel());
        // to keep track of open sockets
        consumer.getAllChannels().add(e.getChannel());
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        LOG.trace("Channel closed: {}", e.getChannel());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent exceptionEvent) throws Exception {
        // only close if we are still allowed to run
        if (consumer.isRunAllowed()) {
            LOG.warn("Closing channel as an exception was thrown from Netty", exceptionEvent.getCause());

            // close channel in case an exception was thrown
            NettyHelper.close(exceptionEvent.getChannel());
        }
    }
    
    @Override
    public void messageReceived(final ChannelHandlerContext ctx, final MessageEvent messageEvent) throws Exception {
        Object in = messageEvent.getMessage();
        LOG.debug("Incoming message: {}", in);

        // create Exchange and let the consumer process it
        final Exchange exchange = consumer.getEndpoint().createExchange(ctx, messageEvent);
        if (consumer.getConfiguration().isSync()) {
            exchange.setPattern(ExchangePattern.InOut);
        }
        // set the exchange charset property for converting
        if (consumer.getConfiguration().getCharsetName() != null) {
            exchange.setProperty(Exchange.CHARSET_NAME, IOHelper.normalizeCharset(consumer.getConfiguration().getCharsetName()));
        }

        // process accordingly to endpoint configuration
        if (consumer.getEndpoint().isSynchronous()) {
            processSynchronously(exchange, messageEvent);
        } else {
            processAsynchronously(exchange, messageEvent);
        }
    }

    private void processSynchronously(final Exchange exchange, final MessageEvent messageEvent) {
        try {
            consumer.getProcessor().process(exchange);
            if (consumer.getConfiguration().isSync()) {
                sendResponse(messageEvent, exchange);
            }
        } catch (Throwable e) {
            consumer.getExceptionHandler().handleException(e);
        }
    }

    private void processAsynchronously(final Exchange exchange, final MessageEvent messageEvent) {
        consumer.getAsyncProcessor().process(exchange, new AsyncCallback() {
            @Override
            public void done(boolean doneSync) {
                // send back response if the communication is synchronous
                try {
                    if (consumer.getConfiguration().isSync()) {
                        sendResponse(messageEvent, exchange);
                    }
                } catch (Throwable e) {
                    consumer.getExceptionHandler().handleException(e);
                }
            }
        });
    }

    private void sendResponse(MessageEvent messageEvent, Exchange exchange) throws Exception {
        Object body;
        if (ExchangeHelper.isOutCapable(exchange)) {
            body = NettyPayloadHelper.getOut(consumer.getEndpoint(), exchange);
        } else {
            body = NettyPayloadHelper.getIn(consumer.getEndpoint(), exchange);
        }

        boolean failed = exchange.isFailed();
        if (failed && !consumer.getEndpoint().getConfiguration().isTransferExchange()) {
            if (exchange.getException() != null) {
                body = exchange.getException();
            } else {
                // failed and no exception, must be a fault
                body = exchange.getOut().getBody();
            }
        }

        if (body == null) {
            noReplyLogger.log("No payload to send as reply for exchange: " + exchange);
            if (consumer.getConfiguration().isDisconnectOnNoReply()) {
                // must close session if no data to write otherwise client will never receive a response
                // and wait forever (if not timing out)
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Closing channel as no payload to send as reply at address: {}", messageEvent.getRemoteAddress());
                }
                NettyHelper.close(messageEvent.getChannel());
            }
        } else {
            // if textline enabled then covert to a String which must be used for textline
            if (consumer.getConfiguration().isTextline()) {
                body = NettyHelper.getTextlineBody(body, exchange, consumer.getConfiguration().getDelimiter(), consumer.getConfiguration().isAutoAppendDelimiter());
            }

            // we got a body to write
            LOG.debug("Writing body: {}", body);
            if (consumer.getConfiguration().isTcp()) {
                NettyHelper.writeBodySync(messageEvent.getChannel(), null, body, exchange);
            } else {
                NettyHelper.writeBodySync(messageEvent.getChannel(), messageEvent.getRemoteAddress(), body, exchange);
            }
        }

        // should channel be closed after complete?
        Boolean close;
        if (ExchangeHelper.isOutCapable(exchange)) {
            close = exchange.getOut().getHeader(NettyConstants.NETTY_CLOSE_CHANNEL_WHEN_COMPLETE, Boolean.class);
        } else {
            close = exchange.getIn().getHeader(NettyConstants.NETTY_CLOSE_CHANNEL_WHEN_COMPLETE, Boolean.class);
        }

        // should we disconnect, the header can override the configuration
        boolean disconnect = consumer.getConfiguration().isDisconnect();
        if (close != null) {
            disconnect = close;
        }
        if (disconnect) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Closing channel when complete at address: {}", messageEvent.getRemoteAddress());
            }
            NettyHelper.close(messageEvent.getChannel());
        }
    }

}
