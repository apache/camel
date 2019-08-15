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
package org.apache.camel.component.netty;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;

public class DefaultNettyCamelStateCorrelationManager implements NettyCamelStateCorrelationManager {

    private final Map<Channel, NettyCamelState> cache = new ConcurrentHashMap<>();

    @Override
    public void putState(Channel channel, NettyCamelState state) {
        cache.put(channel, state);
    }

    @Override
    public void removeState(ChannelHandlerContext ctx, Channel channel) {
        cache.remove(channel);
    }

    @Override
    public NettyCamelState getState(ChannelHandlerContext ctx, Channel channel, Object msg) {
        return cache.get(channel);
    }

    @Override
    public NettyCamelState getState(ChannelHandlerContext ctx, Channel channel, Throwable cause) {
        return cache.get(channel);
    }
}
