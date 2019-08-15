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

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;

/**
 * To manage and correlate state of {@link NettyCamelState} when doing request/reply via {@link NettyProducer}.
 * <p/>
 * This SPI allows custom implementations to correlate the request and replies.
 */
public interface NettyCamelStateCorrelationManager {

    /**
     * Puts the state.
     * <p/>
     * You can get access to the Camel message from the {@link NettyCamelState} instance.
     *
     * @param channel the channel
     * @param state   the Camel state to be stored
     */
    void putState(Channel channel, NettyCamelState state);

    /**
     * Removes the state when the channel is inactive.
     *
     * @param ctx netty channel handler context
     * @param channel the channel
     */
    void removeState(ChannelHandlerContext ctx, Channel channel);

    /**
     * Gets the state when a response message has been received.
     * <p/>
     * If the implementation stores the state temporary in for example a {@link Map} instance
     * then this method should remove the state from the map as its no longer needed. In other
     * words use the {@link Map#remove(Object)} to get and remove the state.
     *
     * @param ctx netty channel handler context
     * @param channel the channel
     * @param msg the response message
     */
    NettyCamelState getState(ChannelHandlerContext ctx, Channel channel, Object msg);

    /**
     * Gets the state when some internal error occurred.
     *
     * @param ctx netty channel handler context
     * @param channel the channel
     * @param cause the error
     */
    NettyCamelState getState(ChannelHandlerContext ctx, Channel channel, Throwable cause);

}
