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

import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.component.netty.NettyConfiguration;
import org.apache.camel.component.netty.NettyConstants;
import org.apache.camel.component.netty.NettyEndpoint;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.codec.http.HttpRequest;

public class NettyHttpEndpoint extends NettyEndpoint {

    private NettyHttpBinding nettyHttpBinding;

    public NettyHttpEndpoint(String endpointUri, NettyHttpComponent component, NettyConfiguration configuration) {
        super(endpointUri, component, configuration);
        this.nettyHttpBinding = component.getNettyHttpBinding();
        if (this.nettyHttpBinding == null) {
            this.nettyHttpBinding = new DefaultNettyHttpBinding();
        }
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        Consumer answer = new NettyHttpConsumer(this, processor, getConfiguration());
        configureConsumer(answer);
        return answer;
    }

    @Override
    public Exchange createExchange(ChannelHandlerContext ctx, MessageEvent messageEvent) {
        Exchange exchange = createExchange();
        exchange.getIn().setHeader(NettyConstants.NETTY_CHANNEL_HANDLER_CONTEXT, ctx);
        exchange.getIn().setHeader(NettyConstants.NETTY_MESSAGE_EVENT, messageEvent);
        exchange.getIn().setHeader(NettyConstants.NETTY_REMOTE_ADDRESS, messageEvent.getRemoteAddress());
        exchange.getIn().setHeader(NettyConstants.NETTY_LOCAL_ADDRESS, messageEvent.getChannel().getLocalAddress());

        HttpRequest request = (HttpRequest) messageEvent.getMessage();
        Message in = getNettyHttpBinding().toCamelMessage(exchange, request);
        exchange.setIn(in);

        return exchange;
    }

    public NettyHttpBinding getNettyHttpBinding() {
        return nettyHttpBinding;
    }

    public void setNettyHttpBinding(NettyHttpBinding nettyHttpBinding) {
        this.nettyHttpBinding = nettyHttpBinding;
    }
}
