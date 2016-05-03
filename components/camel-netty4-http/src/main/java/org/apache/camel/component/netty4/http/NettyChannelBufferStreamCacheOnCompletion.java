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
package org.apache.camel.component.netty4.http;

import org.apache.camel.Exchange;
import org.apache.camel.component.netty4.http.handlers.HttpServerChannelHandler;
import org.apache.camel.support.SynchronizationAdapter;

/**
 * A {@link org.apache.camel.spi.Synchronization} to keep track of the unit of work on the current {@link Exchange}
 * that has the {@link NettyChannelBufferStreamCache} as message body. This cache is wrapping the raw original
 * Netty {@link io.netty.buffer.ByteBuf}. Because the Netty HTTP server ({@link HttpServerChannelHandler}) will
 * close the {@link io.netty.buffer.ByteBuf} when Netty is complete processing the HttpMessage, then any further
 * access to the cache will cause in a buffer unreadable. In the case of Camel async routing engine will
 * handover the processing of the {@link Exchange} to another thread, then we need to keep track of this event
 * so we can do a defensive copy of the netty {@link io.netty.buffer.ByteBuf} so Camel is able to read
 * the content from other threads, while Netty has closed the original {@link io.netty.buffer.ByteBuf}.
 */
public class NettyChannelBufferStreamCacheOnCompletion extends SynchronizationAdapter {

    private final NettyChannelBufferStreamCache cache;

    public NettyChannelBufferStreamCacheOnCompletion(NettyChannelBufferStreamCache cache) {
        this.cache = cache;
    }

    @Override
    public void onDone(Exchange exchange) {
        // okay netty is no longer being active, so we need to signal to the cache that its to preserve the buffer if still in need.
        cache.defensiveCopyBuffer();
    }

    @Override
    public boolean allowHandover() {
        // do not allow handover, so we can do the defensive copy in the onDone method
        return false;
    }

}
