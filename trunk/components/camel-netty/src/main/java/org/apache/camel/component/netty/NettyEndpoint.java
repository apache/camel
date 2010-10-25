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

import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.impl.SynchronousDelegateProducer;
import org.apache.camel.util.ObjectHelper;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.util.Timer;

public class NettyEndpoint extends DefaultEndpoint {
    private NettyConfiguration configuration;
    private Timer timer;

    public NettyEndpoint(String endpointUri, NettyComponent component, NettyConfiguration configuration) {
        super(endpointUri, component);
        this.configuration = configuration;
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        return new NettyConsumer(this, processor, configuration);
    }

    public Producer createProducer() throws Exception {
        Producer answer = new NettyProducer(this, configuration);
        if (isSynchronous()) {
            return new SynchronousDelegateProducer(answer);
        } else {
            return answer;
        }
    }

    public Exchange createExchange(ChannelHandlerContext ctx, MessageEvent messageEvent) {
        Exchange exchange = createExchange();
        exchange.getIn().setHeader(NettyConstants.NETTY_CHANNEL_HANDLER_CONTEXT, ctx);
        exchange.getIn().setHeader(NettyConstants.NETTY_MESSAGE_EVENT, messageEvent);
        exchange.getIn().setHeader(NettyConstants.NETTY_REMOTE_ADDRESS, messageEvent.getRemoteAddress());
        NettyPayloadHelper.setIn(exchange, messageEvent.getMessage());
        return exchange;
    }
    
    public boolean isSingleton() {
        return true;
    }

    public NettyConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(NettyConfiguration configuration) {
        this.configuration = configuration;
    }

    public void setTimer(Timer timer) {
        this.timer = timer;
    }

    public Timer getTimer() {
        return timer;
    }

    @Override
    public void start() throws Exception {
        super.start();
        ObjectHelper.notNull(timer, "timer");
    }

}