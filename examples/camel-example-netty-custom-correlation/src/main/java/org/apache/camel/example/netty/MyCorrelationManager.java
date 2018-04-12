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
package org.apache.camel.example.netty;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.apache.camel.component.netty4.NettyCamelState;
import org.apache.camel.component.netty4.NettyCamelStateCorrelationManager;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MyCorrelationManager implements NettyCamelStateCorrelationManager {

    private static final Logger LOG = LoggerFactory.getLogger(MyCorrelationManager.class);

    private final ConcurrentMap<String, NettyCamelState> map = new ConcurrentHashMap<>();

    @Override
    public void putState(Channel channel, NettyCamelState state) {
        // grab the correlation id
        String body = state.getExchange().getMessage().getBody(String.class);
        // the correlation id is the first part of the message
        String cid = StringHelper.before(body, ":");
        if (ObjectHelper.isEmpty(cid)) {
            throw new IllegalArgumentException("CorrelationID is missing");
        }
        LOG.debug("putState({}) on channel: {}", cid, channel.id());
        map.put(cid, state);
    }

    @Override
    public void removeState(ChannelHandlerContext channelHandlerContext, Channel channel) {
        // noop
    }

    @Override
    public NettyCamelState getState(ChannelHandlerContext channelHandlerContext, Channel channel, Object body) {
        // the correlation id is the first part of the message
        String cid = StringHelper.before(body.toString(), ":");
        if (ObjectHelper.isEmpty(cid)) {
            throw new IllegalArgumentException("CorrelationID is missing");
        }
        LOG.debug("getState({}) on channel: {}", cid, channel.id());
        // lets remove after use as its no longer needed
        return map.remove(cid);
    }

    @Override
    public NettyCamelState getState(ChannelHandlerContext channelHandlerContext, Channel channel, Throwable throwable) {
        // noop
        return null;
    }
}
