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
package org.apache.camel.component.netty.http.handlers;

import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;

import org.apache.camel.Exchange;
import org.apache.camel.component.netty.NettyConsumer;
import org.apache.camel.component.netty.NettyHelper;
import org.apache.camel.component.netty.handlers.ServerChannelHandler;
import org.apache.camel.component.netty.http.NettyHttpConsumer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.jboss.netty.handler.codec.http.HttpHeaders.is100ContinueExpected;
import static org.jboss.netty.handler.codec.http.HttpHeaders.isKeepAlive;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.CONTINUE;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.METHOD_NOT_ALLOWED;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.SERVICE_UNAVAILABLE;
import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * Netty HTTP {@link ServerChannelHandler} that handles the incoming HTTP requests and routes
 * the received message in Camel.
 */
public class HttpServerChannelHandler extends ServerChannelHandler {

    // use NettyHttpConsumer as logger to make it easier to read the logs as this is part of the consumer
    private static final transient Logger LOG = LoggerFactory.getLogger(NettyHttpConsumer.class);
    private final NettyHttpConsumer consumer;
    private HttpRequest request;

    public HttpServerChannelHandler(NettyHttpConsumer consumer) {
        super(consumer);
        this.consumer = consumer;
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent messageEvent) throws Exception {
        // store request, as this channel handler is created per pipeline
        request = (HttpRequest) messageEvent.getMessage();

        LOG.debug("Message received: {}", request);

        if (is100ContinueExpected(request)) {
            // send back http 100 response to continue
            HttpResponse response = new DefaultHttpResponse(HTTP_1_1, CONTINUE);
            messageEvent.getChannel().write(response);
            return;
        }

        if (consumer.isSuspended()) {
            // are we suspended?
            LOG.debug("Consumer suspended, cannot service request {}", request);
            HttpResponse response = new DefaultHttpResponse(HTTP_1_1, SERVICE_UNAVAILABLE);
            response.setHeader(Exchange.CONTENT_TYPE, "text/plain");
            response.setHeader(Exchange.CONTENT_LENGTH, 0);
            response.setContent(ChannelBuffers.copiedBuffer(new byte[]{}));
            messageEvent.getChannel().write(response);
            return;
        }
        if (consumer.getEndpoint().getHttpMethodRestrict() != null
                && !consumer.getEndpoint().getHttpMethodRestrict().contains(request.getMethod().getName())) {
            HttpResponse response = new DefaultHttpResponse(HTTP_1_1, METHOD_NOT_ALLOWED);
            response.setHeader(Exchange.CONTENT_TYPE, "text/plain");
            response.setHeader(Exchange.CONTENT_LENGTH, 0);
            response.setContent(ChannelBuffers.copiedBuffer(new byte[]{}));
            messageEvent.getChannel().write(response);
            return;
        }
        if ("TRACE".equals(request.getMethod().getName()) && !consumer.getEndpoint().isTraceEnabled()) {
            HttpResponse response = new DefaultHttpResponse(HTTP_1_1, METHOD_NOT_ALLOWED);
            response.setHeader(Exchange.CONTENT_TYPE, "text/plain");
            response.setHeader(Exchange.CONTENT_LENGTH, 0);
            response.setContent(ChannelBuffers.copiedBuffer(new byte[]{}));
            messageEvent.getChannel().write(response);
            return;
        }

        // let Camel process this message
        super.messageReceived(ctx, messageEvent);
    }

    @Override
    protected void beforeProcess(Exchange exchange, MessageEvent messageEvent) {
        if (consumer.getConfiguration().isBridgeEndpoint()) {
            exchange.setProperty(Exchange.SKIP_GZIP_ENCODING, Boolean.TRUE);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent exceptionEvent) throws Exception {
        // only close if we are still allowed to run
        if (consumer.isRunAllowed()) {

            if (exceptionEvent.getCause() instanceof ClosedChannelException) {
                LOG.debug("Channel already closed. Ignoring this exception.");
            } else {
                LOG.warn("Closing channel as an exception was thrown from Netty", exceptionEvent.getCause());
                // close channel in case an exception was thrown
                NettyHelper.close(exceptionEvent.getChannel());
            }
        }
    }

    @Override
    protected ChannelFutureListener createResponseFutureListener(NettyConsumer consumer, Exchange exchange, SocketAddress remoteAddress) {
        // make sure to close channel if not keep-alive
        if (request != null && isKeepAlive(request)) {
            LOG.trace("Request has Connection: keep-alive so Channel is not being closed");
            return null;
        } else {
            LOG.trace("Request is not Connection: close so Channel is being closed");
            return ChannelFutureListener.CLOSE;
        }
    }

    @Override
    protected Object getResponseBody(Exchange exchange) throws Exception {
        // use the binding
        if (exchange.hasOut()) {
            return consumer.getEndpoint().getNettyHttpBinding().toNettyResponse(exchange.getOut(), consumer.getConfiguration());
        } else {
            return consumer.getEndpoint().getNettyHttpBinding().toNettyResponse(exchange.getIn(), consumer.getConfiguration());
        }
    }
}
