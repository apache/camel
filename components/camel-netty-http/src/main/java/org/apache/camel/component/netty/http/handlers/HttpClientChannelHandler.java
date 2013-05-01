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

import org.apache.camel.component.netty.NettyProducer;
import org.apache.camel.component.netty.handlers.ClientChannelHandler;
import org.apache.camel.component.netty.http.NettyHttpProducer;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpClientChannelHandler extends ClientChannelHandler {

    // use NettyHttpConsumer as logger to make it easier to read the logs as this is part of the producer
    private static final transient Logger LOG = LoggerFactory.getLogger(NettyHttpProducer.class);

    public HttpClientChannelHandler(NettyProducer producer) {
        super(producer);
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent messageEvent) throws Exception {
        super.messageReceived(ctx, messageEvent);    //To change body of overridden methods use File | Settings | File Templates.
    }

}
