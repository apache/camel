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

import java.net.SocketAddress;

import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;

/**
 * Helper class used internally by camel-netty using Netty.
 *
 * @version $Revision$
 */
public final class NettyHelper {

    private static final transient Log LOG = LogFactory.getLog(NettyHelper.class);

    private NettyHelper() {
        // Utility class
    }

    /**
     * Writes the given body to Netty channel. Will wait until the body has been written.
     *
     * @param channel  the Netty channel
     * @param body     the body to write (send)
     * @param exchange the exchange
     * @throws CamelExchangeException is thrown if the body could not be written for some reasons
     *                                (eg remote connection is closed etc.)
     */
    public static void writeBodySync(Channel channel, SocketAddress remoteAddress, Object body, Exchange exchange) throws CamelExchangeException {
        // the write operation is asynchronous. Use future to wait until the session has been written
        ChannelFuture future;
        if (remoteAddress != null) {
            future = channel.write(body, remoteAddress);
        } else {
            future = channel.write(body);
        }

        // wait for the write
        if (LOG.isTraceEnabled()) {
            LOG.trace("Waiting for write to complete");
        }
        future.awaitUninterruptibly();

        // if it was not a success then thrown an exception
        if (!future.isSuccess()) {
            LOG.warn("Cannot write body: " + body + " using channel: " + channel);
            throw new CamelExchangeException("Cannot write body", exchange, future.getCause());
        }
    }

    public static void close(Channel channel) {
        if (channel != null) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Closing channel: " + channel);
            }
            channel.close().awaitUninterruptibly();
        }
    }

}
