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

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.ThreadFactory;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.DefaultAddressedEnvelope;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.util.concurrent.CamelThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class used internally by camel-netty using Netty.
 */
public final class NettyHelper {

    private static final Logger LOG = LoggerFactory.getLogger(NettyHelper.class);

    private NettyHelper() {
        // Utility class
    }

    /**
     * Gets the string body to be used when sending with the textline codec.
     *
     * @param  body                               the current body
     * @param  exchange                           the exchange
     * @param  delimiter                          the textline delimiter
     * @param  autoAppendDelimiter                whether absent delimiter should be auto appended
     * @return                                    the string body to send
     * @throws NoTypeConversionAvailableException is thrown if the current body could not be converted to a String type
     */
    public static String getTextlineBody(
            Object body, Exchange exchange, TextLineDelimiter delimiter, boolean autoAppendDelimiter)
            throws NoTypeConversionAvailableException {
        String s = exchange.getContext().getTypeConverter().mandatoryConvertTo(String.class, exchange, body);

        // auto append delimiter if missing?
        if (autoAppendDelimiter) {
            if (TextLineDelimiter.LINE.equals(delimiter)) {
                // line delimiter so ensure it ends with newline
                if (!s.endsWith("\n")) {
                    LOG.trace("Auto appending missing newline delimiter to body");
                    s = s + "\n";
                }
            } else {
                // null delimiter so ensure it ends with null
                if (!s.endsWith("\u0000")) {
                    LOG.trace("Auto appending missing null delimiter to body");
                    s = s + "\u0000";
                }
            }
        }

        return s;
    }

    /**
     * Writes the given body to Netty channel. Will <b>not</b >wait until the body has been written.
     *
     * @param log           logger to use
     * @param channel       the Netty channel
     * @param remoteAddress the remote address when using UDP
     * @param body          the body to write (send)
     * @param listener      listener with work to be executed when the operation is complete
     */
    public static void writeBodyAsync(
            Logger log, Channel channel, SocketAddress remoteAddress, Object body,
            ChannelFutureListener listener) {
        ChannelFuture future;
        if (remoteAddress != null) {
            if (log.isDebugEnabled()) {
                log.debug("Channel: {} remote address: {} writing body: {}", channel, remoteAddress, body);
            }
            // Need to create AddressedEnvelope to setup the address information here
            DefaultAddressedEnvelope<Object, InetSocketAddress> ae
                    = new DefaultAddressedEnvelope<>(body, (InetSocketAddress) remoteAddress);
            future = channel.writeAndFlush(ae);
        } else {
            if (log.isDebugEnabled()) {
                log.debug("Channel: {} writing body: {}", channel, body);
            }
            // In netty we need to call channel flush to send out the message
            future = channel.writeAndFlush(body);
        }

        if (listener != null) {
            future.addListener(listener);
        }
    }

    /**
     * Closes the given channel asynchronously
     *
     * @param channel the channel to close
     */
    public static void close(Channel channel) {
        if (channel != null) {
            channel.close().addListener((ChannelFuture future) -> LOG.trace("Channel closed: {}", future.channel()));
        }
    }

    /**
     * Creates a {@link DefaultEventExecutorGroup} with the given name and maximum thread pool size.
     */
    public static EventExecutorGroup createExecutorGroup(CamelContext camelContext, String name, int threads) {
        // Provide the executor service for the application
        // and use a Camel thread factory so we have consistent thread namings
        // we should use a shared thread pool as recommended by Netty
        String pattern = camelContext.getExecutorServiceManager().getThreadNamePattern();
        ThreadFactory factory = new CamelThreadFactory(pattern, name, true);
        return new DefaultEventExecutorGroup(threads, factory);
    }

}
