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

import org.apache.camel.CamelException;
import org.apache.camel.component.netty.NettyHelper;
import org.apache.camel.component.netty.NettyProducer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;

@ChannelPipelineCoverage("all")
public class ClientChannelHandler extends SimpleChannelUpstreamHandler {
    private static final transient Log LOG = LogFactory.getLog(ClientChannelHandler.class);
    private NettyProducer producer;
    private Object response;
    
    public ClientChannelHandler(NettyProducer producer) {
        super();
        this.producer = producer;
    }

    @Override
    public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent channelStateEvent) throws Exception {
        // to keep track of open sockets
        producer.getAllChannels().add(channelStateEvent.getChannel());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent exceptionEvent) throws Exception {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Closing channel as an exception was thrown from Netty", exceptionEvent.getCause());
        }
        // close channel in case an exception was thrown
        NettyHelper.close(exceptionEvent.getChannel());

        // must wrap and rethrow since cause can be of Throwable and we must only throw Exception
        throw new CamelException(exceptionEvent.getCause());
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent messageEvent) throws Exception {
        setResponse(messageEvent.getMessage());

        if (LOG.isDebugEnabled()) {
            LOG.debug("Incoming message:" + response);
        }

        if (producer.getConfiguration().isSync()) {
            producer.getCountdownLatch().countDown();
        }        
    }

    public Object getResponse() {
        return response;
    }

    public void setResponse(Object response) {
        this.response = response;
    }
    
}
