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
package org.apache.camel.component.netty.handlers;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import org.apache.camel.component.netty.NettyHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ensures channel closure on SSL/TLS handshake failure as a defense-in-depth safety net, complementing Netty's built-in
 * behavior.
 * <p>
 * Netty 4.x's {@code SslHandler} already closes the channel on handshake failure natively. This handler provides an
 * explicit safety net by listening for {@link SslHandshakeCompletionEvent} and closing the channel on failure,
 * replacing the removed Netty 3.x {@code SslHandler.setCloseOnSSLException(true)} API that was commented out since the
 * Netty 4 migration (CAMEL-6555).
 */
@ChannelHandler.Sharable
public class SslHandshakeFailureHandler extends ChannelInboundHandlerAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(SslHandshakeFailureHandler.class);

    public static final SslHandshakeFailureHandler INSTANCE = new SslHandshakeFailureHandler();

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof SslHandshakeCompletionEvent handshakeEvent) {
            if (!handshakeEvent.isSuccess()) {
                LOG.debug("SSL/TLS handshake failed on channel {}, closing: {}",
                        ctx.channel(), handshakeEvent.cause().getMessage());
                NettyHelper.close(ctx.channel());
            }
        }
        super.userEventTriggered(ctx, evt);
    }
}
