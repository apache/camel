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
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.netty.NettyCamelState;
import org.apache.camel.component.netty.NettyConstants;
import org.apache.camel.component.netty.NettyHelper;
import org.apache.camel.component.netty.NettyPayloadHelper;
import org.apache.camel.component.netty.NettyProducer;
import org.apache.camel.util.ExchangeHelper;
import org.jboss.netty.channel.ChannelHandler;
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
public class ClientChannelHandler extends SimpleChannelUpstreamHandler {
    // use NettyProducer as logger to make it easier to read the logs as this is part of the producer
    private static final Logger LOG = LoggerFactory.getLogger(NettyProducer.class);
    private final NettyProducer producer;
    private volatile boolean messageReceived;
    private volatile boolean exceptionHandled;

    public ClientChannelHandler(NettyProducer producer) {
        this.producer = producer;
    }

    @Override
    public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent channelStateEvent) throws Exception {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Channel open: {}", ctx.getChannel());
        }
        // to keep track of open sockets
        producer.getAllChannels().add(channelStateEvent.getChannel());
        // make sure the event can be processed by other handlers
        super.channelOpen(ctx, channelStateEvent);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent exceptionEvent) throws Exception {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Exception caught at Channel: " + ctx.getChannel(), exceptionEvent.getCause());
        }
         
        if (exceptionHandled) {
            // ignore subsequent exceptions being thrown
            return;
        }

        exceptionHandled = true;
        Throwable cause = exceptionEvent.getCause();

        if (LOG.isDebugEnabled()) {
            LOG.debug("Closing channel as an exception was thrown from Netty", cause);
        }

        Exchange exchange = getExchange(ctx);
        AsyncCallback callback = getAsyncCallback(ctx);

        // the state may not be set
        if (exchange != null && callback != null) {
            Throwable initialCause = exchange.getException();
            if (initialCause != null && initialCause.getCause() == null) {
                initialCause.initCause(cause);
            } else {
                // set the cause on the exchange
                exchange.setException(cause);
            }

            // close channel in case an exception was thrown
            NettyHelper.close(exceptionEvent.getChannel());

            // signal callback
            callback.done(false);
        }
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Channel closed: {}", ctx.getChannel());
        }

        Exchange exchange = getExchange(ctx);
        AsyncCallback callback = getAsyncCallback(ctx);

        // remove state
        producer.removeState(ctx.getChannel());

        // to keep track of open sockets
        producer.getAllChannels().remove(ctx.getChannel());

        // this channel is maybe closing graceful and the exchange is already done
        // and if so we should not trigger an exception
        boolean doneUoW = exchange.getUnitOfWork() == null;

        if (producer.getConfiguration().isSync() && !doneUoW && !messageReceived && !exceptionHandled) {
            // To avoid call the callback.done twice 
            exceptionHandled = true;
            // session was closed but no message received. This could be because the remote server had an internal error
            // and could not return a response. We should count down to stop waiting for a response
            if (LOG.isDebugEnabled()) {
                LOG.debug("Channel closed but no message received from address: {}", producer.getConfiguration().getAddress());
            }
            exchange.setException(new CamelExchangeException("No response received from remote server: " + producer.getConfiguration().getAddress(), exchange));
            // signal callback
            callback.done(false);
        }

        // make sure the event can be processed by other handlers
        super.channelClosed(ctx, e);
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent messageEvent) throws Exception {
        messageReceived = true;

        if (LOG.isTraceEnabled()) {
            LOG.trace("Message received: {}", messageEvent);
        }

        ChannelHandler handler = ctx.getPipeline().get("timeout");
        if (handler != null) {
            LOG.trace("Removing timeout channel as we received message");
            ctx.getPipeline().remove(handler);
        }
        
        Exchange exchange = getExchange(ctx);
        if (exchange == null) {
            // we just ignore the received message as the channel is closed
            return;
        }     

        AsyncCallback callback = getAsyncCallback(ctx);

        Message message;
        try {
            message = getResponseMessage(exchange, messageEvent);
        } catch (Exception e) {
            exchange.setException(e);
            callback.done(false);
            return;
        }

        // set the result on either IN or OUT on the original exchange depending on its pattern
        if (ExchangeHelper.isOutCapable(exchange)) {
            exchange.setOut(message);
        } else {
            exchange.setIn(message);
        }

        try {
            // should channel be closed after complete?
            Boolean close;
            if (ExchangeHelper.isOutCapable(exchange)) {
                close = exchange.getOut().getHeader(NettyConstants.NETTY_CLOSE_CHANNEL_WHEN_COMPLETE, Boolean.class);
            } else {
                close = exchange.getIn().getHeader(NettyConstants.NETTY_CLOSE_CHANNEL_WHEN_COMPLETE, Boolean.class);
            }
            
            // check the setting on the exchange property
            if (close == null) {
                close = exchange.getProperty(NettyConstants.NETTY_CLOSE_CHANNEL_WHEN_COMPLETE, Boolean.class);
            }
            
            // should we disconnect, the header can override the configuration
            boolean disconnect = producer.getConfiguration().isDisconnect();
            if (close != null) {
                disconnect = close;
            }
            if (disconnect) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Closing channel when complete at address: {}", producer.getConfiguration().getAddress());
                }
                NettyHelper.close(ctx.getChannel());
            }
        } finally {
            // signal callback
            callback.done(false);
        }
    }

    /**
     * Gets the Camel {@link Message} to use as the message to be set on the current {@link Exchange} when
     * we have received a reply message.
     * <p/>
     *
     * @param exchange      the current exchange
     * @param messageEvent  the incoming event which has the response message from Netty.
     * @return the Camel {@link Message} to set on the current {@link Exchange} as the response message.
     * @throws Exception is thrown if error getting the response message
     */
    protected Message getResponseMessage(Exchange exchange, MessageEvent messageEvent) throws Exception {
        Object body = messageEvent.getMessage();
        if (LOG.isDebugEnabled()) {
            LOG.debug("Channel: {} received body: {}", new Object[]{messageEvent.getChannel(), body});
        }

        // if textline enabled then covert to a String which must be used for textline
        if (producer.getConfiguration().isTextline()) {
            body = producer.getContext().getTypeConverter().mandatoryConvertTo(String.class, exchange, body);
        }

        // set the result on either IN or OUT on the original exchange depending on its pattern
        if (ExchangeHelper.isOutCapable(exchange)) {
            NettyPayloadHelper.setOut(exchange, body);
            return exchange.getOut();
        } else {
            NettyPayloadHelper.setIn(exchange, body);
            return exchange.getIn();
        }
    }

    protected Exchange getExchange(ChannelHandlerContext ctx) {
        NettyCamelState state = producer.getState(ctx.getChannel());
        return state != null ? state.getExchange() : null;
    }

    private AsyncCallback getAsyncCallback(ChannelHandlerContext ctx) {
        NettyCamelState state = producer.getState(ctx.getChannel());
        return state != null ? state.getCallback() : null;
    }

}
