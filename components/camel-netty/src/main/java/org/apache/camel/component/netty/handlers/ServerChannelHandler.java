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

import java.net.InetSocketAddress;

import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.component.netty.NettyConsumer;
import org.apache.camel.component.netty.NettyEndpoint;
import org.apache.camel.util.ExchangeHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;

@ChannelPipelineCoverage("all")
public class ServerChannelHandler extends SimpleChannelUpstreamHandler {
    private static final transient Log LOG = LogFactory.getLog(ServerChannelHandler.class);
    private NettyConsumer consumer;
    
    public ServerChannelHandler(NettyConsumer consumer) {
        super();
        this.consumer = consumer;    
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent exceptionEvent)
        throws Exception {
        if (LOG.isDebugEnabled()) {
            LOG.debug("An exception was caught by the ServerChannelHandler during communication", exceptionEvent.getCause());
        }
    }
    
    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent messageEvent)
        throws Exception {
        Object in = messageEvent.getMessage();
        if (LOG.isDebugEnabled()) {
            if (in instanceof byte[]) {
                in = consumer.getEndpoint().getCamelContext().getTypeConverter().convertTo(String.class, in);
            }
            LOG.debug("Incoming message: " + in);
        }
        
        // Dispatch exchange along the route and receive the final resulting exchange
        dispatchExchange(ctx, messageEvent, in); 
    }

    private void sendResponsetoChannel(MessageEvent messageEvent, Exchange exchange) throws Exception {
        ChannelFuture future;
        Object body;
        if (ExchangeHelper.isOutCapable(exchange)) {
            body = exchange.getOut().getBody();
        } else {
            body = exchange.getIn().getBody();
        }
        
        if (exchange.isFailed()) {
            if (exchange.getException() == null) {
                // fault detected
                body = exchange.getOut().getBody();
            } else {
                body = exchange.getException();
            }
        }
        
        if (body == null) {
            LOG.warn("No Oubound Response received following route completion: " + exchange);
            LOG.warn("A response cannot be sent to the Client");
            messageEvent.getChannel().close();
        }
        
        if (consumer.getConfiguration().getProtocol().equalsIgnoreCase("udp")) {
            future = messageEvent.getChannel().write(body, messageEvent.getRemoteAddress());
        } else {
            future = messageEvent.getChannel().write(body);
        }
        
        if (!future.isSuccess()) {
            String hostname = ((InetSocketAddress)messageEvent.getChannel().getRemoteAddress()).getHostName();
            int port = ((InetSocketAddress)messageEvent.getChannel().getRemoteAddress()).getPort();
            throw new CamelExchangeException("Could not send response via Channel to remote host " + hostname + " and port " + port, exchange);
        }
        
        if (LOG.isDebugEnabled()) {
            if (body instanceof byte[]) {
                body = consumer.getEndpoint().getCamelContext().getTypeConverter().convertTo(String.class, body);
            }
            LOG.debug("Sent Outgoing message: " + body);
        }        
    }

    private void dispatchExchange(ChannelHandlerContext ctx, MessageEvent messageEvent, Object in) throws Exception {        
        if (LOG.isDebugEnabled()) {
            LOG.debug("Consumer Dispatching the Incoming exchange along the route");
        }

        Exchange exchange = ((NettyEndpoint)consumer.getEndpoint()).createExchange(ctx, messageEvent);
        if (consumer.getConfiguration().isSync()) {
            exchange.setPattern(ExchangePattern.InOut);
        }
        exchange.getIn().setBody(in);
        
        try {
            consumer.getProcessor().process(exchange);
        } catch (Exception exception) {
            throw new CamelExchangeException("Error in consumer while dispatching exchange for further processing", exchange);
        }
        
        // Send back response if the communication is synchronous
        if (consumer.getConfiguration().isSync()) {
            sendResponsetoChannel(messageEvent, exchange);
        }
    }
    
}
