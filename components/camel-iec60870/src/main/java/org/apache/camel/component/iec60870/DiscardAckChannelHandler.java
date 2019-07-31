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
package org.apache.camel.component.iec60870;

import java.util.HashSet;
import java.util.Set;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.eclipse.neoscada.protocol.iec60870.asdu.message.AbstractMessage;
import org.eclipse.neoscada.protocol.iec60870.asdu.types.Cause;
import org.eclipse.neoscada.protocol.iec60870.asdu.types.StandardCause;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DiscardAckChannelHandler extends ChannelInboundHandlerAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(DiscardAckChannelHandler.class);

    private final Set<Cause> discards = new HashSet<>();

    public DiscardAckChannelHandler() {
        this.discards.add(StandardCause.ACTIVATION_CONFIRM);
        this.discards.add(StandardCause.ACTIVATION_TERMINATION);
        this.discards.add(StandardCause.DEACTIVATION_CONFIRM);
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
        if (msg instanceof AbstractMessage) {
            final AbstractMessage amsg = (AbstractMessage)msg;
            final Cause cause = amsg.getHeader().getCauseOfTransmission().getCause();
            if (this.discards.contains(cause)) {
                LOG.debug("Discarding: {}", cause);
                return;
            }
        }
        super.channelRead(ctx, msg);
    }
}
