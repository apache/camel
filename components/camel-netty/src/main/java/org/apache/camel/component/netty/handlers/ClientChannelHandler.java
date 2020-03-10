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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.netty.NettyCamelState;
import org.apache.camel.component.netty.NettyConfiguration;
import org.apache.camel.component.netty.NettyConstants;
import org.apache.camel.component.netty.NettyHelper;
import org.apache.camel.component.netty.NettyPayloadHelper;
import org.apache.camel.component.netty.NettyProducer;
import org.apache.camel.support.ExchangeHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client handler which cannot be shared
 */
public class ClientChannelHandler extends SimpleChannelInboundHandler<Object> {
    // use NettyProducer as logger to make it easier to read the logs as this is part of the producer
    private static final Logger LOG = LoggerFactory.getLogger(NettyProducer.class);
    private final NettyProducer producer;
    private volatile boolean messageReceived;
    private volatile boolean exceptionHandled;

    public ClientChannelHandler(NettyProducer producer) {
        this.producer = producer;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Channel open: {}", ctx.channel());
        }
        // to keep track of open sockets
        producer.getAllChannels().add(ctx.channel());

        super.channelActive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Exception caught at Channel: {}", ctx.channel(), cause);
        }

        if (exceptionHandled) {
            // ignore subsequent exceptions being thrown
            return;
        }

        exceptionHandled = true;

        if (LOG.isDebugEnabled()) {
            LOG.debug("Closing channel as an exception was thrown from Netty", cause);
        }

        NettyCamelState state = getState(ctx, cause);
        Exchange exchange = state != null ? state.getExchange() : null;
        AsyncCallback callback = state != null ? state.getCallback() : null;

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
            NettyHelper.close(ctx.channel());

            // signal callback
            callback.done(false);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Channel closed: {}", ctx.channel());
        }

        NettyCamelState state = getState(ctx, null);
        Exchange exchange = state != null ? state.getExchange() : null;
        AsyncCallback callback = state != null ? state.getCallback() : null;

        // remove state
        producer.getCorrelationManager().removeState(ctx, ctx.channel());

        // to keep track of open sockets
        producer.getAllChannels().remove(ctx.channel());

        if (exchange != null) {
            // this channel is maybe closing graceful and the exchange is already done
            // and if so we should not trigger an exception
            boolean doneUoW = exchange.getUnitOfWork() == null;

            NettyConfiguration configuration = producer.getConfiguration();
            if (configuration.isSync() && !doneUoW && !messageReceived && !exceptionHandled) {
                // To avoid call the callback.done twice
                exceptionHandled = true;
                // session was closed but no message received. This could be because the remote server had an internal error
                // and could not return a response. We should count down to stop waiting for a response
                String address = configuration.getAddress();
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Channel is inactive but no message received from address: {}", address);
                }
                // don't fail the exchange if we actually specify to disconnect
                if (!configuration.isDisconnect()) {
                    exchange.setException(new CamelExchangeException("No response received from remote server: " + address, exchange));
                }
                // signal callback
                callback.done(false);
            }
        }

        // make sure the event can be processed by other handlers
        super.channelInactive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        messageReceived = true;

        if (LOG.isTraceEnabled()) {
            LOG.trace("Message received: {}", msg);
        }

        NettyCamelState state = getState(ctx, msg);
        Exchange exchange = state != null ? state.getExchange() : null;
        if (exchange == null) {
            // we just ignore the received message as the channel is closed
            return;
        }
        AsyncCallback callback = state.getCallback();

        Message message;
        try {
            message = getResponseMessage(exchange, ctx, msg);
        } catch (Exception e) {
            exchange.setException(e);
            callback.done(false);
            return;
        }

        Boolean continueWaitForAnswer = exchange.getProperty(NettyConstants.NETTY_CLIENT_CONTINUE, Boolean.class);
        if (continueWaitForAnswer != null && continueWaitForAnswer) {
            exchange.removeProperty(NettyConstants.NETTY_CLIENT_CONTINUE);
            // Leave channel open and continue wait for an answer.
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
            // we should not close if we are reusing the channel
            if (!producer.getConfiguration().isReuseChannel() && disconnect) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Closing channel when complete at address: {}", producer.getConfiguration().getAddress());
                }
                NettyHelper.close(ctx.channel());
            }
        } finally {
            // signal callback
            callback.done(false);
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        // reset flag after we have read the complete
        messageReceived = false;
        super.channelReadComplete(ctx);
    }

    /**
     * Gets the Camel {@link Message} to use as the message to be set on the current {@link Exchange} when
     * we have received a reply message.
     * <p/>
     *
     * @param exchange      the current exchange
     * @param ctx       the channel handler context
     * @param message  the incoming event which has the response message from Netty.
     * @return the Camel {@link Message} to set on the current {@link Exchange} as the response message.
     * @throws Exception is thrown if error getting the response message
     */
    protected Message getResponseMessage(Exchange exchange, ChannelHandlerContext ctx, Object message) throws Exception {
        Object body = message;

        if (LOG.isDebugEnabled()) {
            LOG.debug("Channel: {} received body: {}", ctx.channel(), body);
        }

        // if textline enabled then covert to a String which must be used for textline
        if (producer.getConfiguration().isTextline()) {
            body = producer.getContext().getTypeConverter().mandatoryConvertTo(String.class, exchange, message);
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

    private NettyCamelState getState(ChannelHandlerContext ctx, Object msg) {
        return producer.getCorrelationManager().getState(ctx, ctx.channel(), msg);
    }

    private NettyCamelState getState(ChannelHandlerContext ctx, Throwable cause) {
        return producer.getCorrelationManager().getState(ctx, ctx.channel(), cause);
    }

}
