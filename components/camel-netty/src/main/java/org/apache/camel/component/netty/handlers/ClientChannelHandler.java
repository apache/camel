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

import java.util.concurrent.CountDownLatch;

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
    private Object message;
    private Throwable cause;
    private boolean messageReceived;

    public ClientChannelHandler(NettyProducer producer) {
        super();
        this.producer = producer;
    }

    public void reset() {
        this.message = null;
        this.cause = null;
        this.messageReceived = false;
    }

    @Override
    public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent channelStateEvent) throws Exception {
        // to keep track of open sockets
        producer.getAllChannels().add(channelStateEvent.getChannel());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent exceptionEvent) throws Exception {
        this.message = null;
        this.messageReceived = false;
        this.cause = exceptionEvent.getCause();

        if (LOG.isDebugEnabled()) {
            LOG.debug("Closing channel as an exception was thrown from Netty", cause);
        }
        // close channel in case an exception was thrown
        NettyHelper.close(exceptionEvent.getChannel());
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        if (producer.getConfiguration().isSync() && !messageReceived) {
            // sync=true (InOut mode) so we expected a message as reply but did not get one before the session is closed
            if (LOG.isDebugEnabled()) {
                LOG.debug("Channel closed but no message received from address: " + producer.getConfiguration().getAddress());
            }
            // session was closed but no message received. This could be because the remote server had an internal error
            // and could not return a response. We should count down to stop waiting for a response
            countDown();
        }
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent messageEvent) throws Exception {
        message = messageEvent.getMessage();
        messageReceived = true;
        cause = null;

        if (LOG.isDebugEnabled()) {
            LOG.debug("Message received: " + message);
        }

        // signal we have received message
        countDown();
    }

    protected void countDown() {
        if (producer.getConfiguration().isSync()) {
            producer.getCountdownLatch().countDown();
        }
    }

    public Object getMessage() {
        return message;
    }

    public boolean isMessageReceived() {
        return messageReceived;
    }

    public Throwable getCause() {
        return cause;
    }
}
