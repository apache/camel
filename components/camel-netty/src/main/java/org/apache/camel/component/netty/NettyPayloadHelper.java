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

import io.netty.buffer.ByteBuf;
import io.netty.channel.AddressedEnvelope;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultExchangeHolder;
import org.apache.camel.support.ExchangeHelper;

/**
 * Helper to get and set the correct payload when transferring data using camel-netty. Always use this helper instead of
 * direct access on the exchange object.
 * <p/>
 * This helper ensures that we can also transfer exchange objects over the wire using the <tt>transferExchange=true</tt>
 * option.
 */
public final class NettyPayloadHelper {

    /**
     * The headers and exchange properties holding objects of the Netty transport of this side (the channel, the socket
     * addresses, the SSL session). They are not transferred with transferExchange=true: they mean nothing on the other
     * side, and the decoder of the other side may reject their classes (the default deserialization filter of the
     * camel-netty {@code ObjectDecoder} rejects java.net classes).
     */
    private static final String[] TRANSPORT_KEYS = {
            NettyConstants.NETTY_CHANNEL_HANDLER_CONTEXT, NettyConstants.NETTY_MESSAGE_EVENT,
            NettyConstants.NETTY_REMOTE_ADDRESS, NettyConstants.NETTY_LOCAL_ADDRESS, NettyConstants.NETTY_SSL_SESSION,
            NettyConstants.NETTY_CHANNEL };

    private NettyPayloadHelper() {
        //Helper class
    }

    public static Object getIn(NettyEndpoint endpoint, Exchange exchange) throws InvalidPayloadException {
        if (endpoint.getConfiguration().isTransferExchange()) {
            // we should transfer the entire exchange over the wire (includes in/out)
            return marshal(endpoint, exchange);
        } else {
            return getBody(endpoint, exchange.getIn());
        }
    }

    public static Object getOut(NettyEndpoint endpoint, Exchange exchange) throws InvalidPayloadException {
        if (endpoint.getConfiguration().isTransferExchange()) {
            // we should transfer the entire exchange over the wire (includes in/out), and as for the request only
            // include serialized values in headers, properties and variables when allowed
            return marshal(endpoint, exchange);
        } else {
            return getBody(endpoint, exchange.getOut());
        }
    }

    private static DefaultExchangeHolder marshal(NettyEndpoint endpoint, Exchange exchange) {
        Exchange source = exchange;
        if (hasTransportValues(exchange)) {
            // marshal a copy without the Netty transport values, and leave the exchange as is
            source = ExchangeHelper.createCopy(exchange, true);
            for (String key : TRANSPORT_KEYS) {
                source.getIn().removeHeader(key);
                if (source.hasOut()) {
                    source.getOut().removeHeader(key);
                }
                source.removeProperty(key);
            }
        }
        return DefaultExchangeHolder.marshal(source, true, endpoint.getConfiguration().isAllowSerializedHeaders());
    }

    private static boolean hasTransportValues(Exchange exchange) {
        for (String key : TRANSPORT_KEYS) {
            if (exchange.getIn().getHeader(key) != null
                    || exchange.hasOut() && exchange.getOut().getHeader(key) != null
                    || exchange.getProperty(key) != null) {
                return true;
            }
        }
        return false;
    }

    private static Object getBody(NettyEndpoint endpoint, Message message) throws InvalidPayloadException {
        if (endpoint.getConfiguration().isUseByteBuf() && message.getBody() != null) {
            // Just leverage the type converter, and fail if the body cannot be converted instead of sending nothing
            return message.getMandatoryBody(ByteBuf.class);
        } else {
            // normal transfer using the body only
            return message.getBody();
        }
    }

    public static void setIn(Exchange exchange, Object payload) {
        if (payload instanceof DefaultExchangeHolder defaultExchangeHolder) {
            DefaultExchangeHolder.unmarshal(exchange, defaultExchangeHolder);
        } else if (payload instanceof AddressedEnvelope) {
            @SuppressWarnings("unchecked")
            AddressedEnvelope<Object, InetSocketAddress> dp = (AddressedEnvelope<Object, InetSocketAddress>) payload;
            // need to check if the content is ExchangeHolder
            if (dp.content() instanceof DefaultExchangeHolder defaultExchangeHolder) {
                DefaultExchangeHolder.unmarshal(exchange, defaultExchangeHolder);
            } else {
                // need to take out the payload here
                exchange.getIn().setBody(dp.content());
            }
            // setup the sender address here for sending the response message back
            exchange.setProperty(NettyConstants.NETTY_REMOTE_ADDRESS, dp.sender());
            // setup the remote address to the message header at the same time
            exchange.getIn().setHeader(NettyConstants.NETTY_REMOTE_ADDRESS, dp.sender());
        } else {
            // normal transfer using the body only
            exchange.getIn().setBody(payload);
        }
    }

    public static void setOut(Exchange exchange, Object payload) {
        if (payload instanceof DefaultExchangeHolder defaultExchangeHolder) {
            DefaultExchangeHolder.unmarshal(exchange, defaultExchangeHolder);
        } else if (payload instanceof AddressedEnvelope) {
            @SuppressWarnings("unchecked")
            AddressedEnvelope<Object, InetSocketAddress> dp = (AddressedEnvelope<Object, InetSocketAddress>) payload;
            // need to check if the content is ExchangeHolder
            if (dp.content() instanceof DefaultExchangeHolder defaultExchangeHolder) {
                DefaultExchangeHolder.unmarshal(exchange, defaultExchangeHolder);
            } else {
                // need to take out the payload here
                exchange.getOut().setBody(dp.content());
            }
            // setup the sender address here for sending the response message back
            exchange.setProperty(NettyConstants.NETTY_REMOTE_ADDRESS, dp.sender());
        } else {
            // normal transfer using the body only and preserve the headers
            exchange.getOut().setHeaders(exchange.getIn().getHeaders());
            exchange.getOut().setBody(payload);
        }
    }

}
