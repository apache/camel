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
package org.apache.camel.component.mina;

import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultExchangeHolder;

/**
 * Helper to get and set the correct payload when transferring data using camel-mina. Always use this helper instead of
 * direct access on the exchange object.
 * <p/>
 * This helper ensures that we can also transfer exchange objects over the wire using the <tt>transferExchange=true</tt>
 * option.
 */
public final class MinaPayloadHelper {

    private MinaPayloadHelper() {
        //Utility Class
    }

    public static Object getIn(MinaEndpoint endpoint, Exchange exchange) {
        if (endpoint.getConfiguration().isTransferExchange()) {
            // we should transfer the entire exchange over the wire (includes in/out)
            return DefaultExchangeHolder.marshal(exchange);
        } else {
            // normal transfer using the body only
            return exchange.getIn().getBody();
        }
    }

    public static Object getOut(MinaEndpoint endpoint, Exchange exchange) {
        if (endpoint.getConfiguration().isTransferExchange()) {
            // we should transfer the entire exchange over the wire (includes in/out)
            return DefaultExchangeHolder.marshal(exchange);
        } else {
            // normal transfer using the body only
            return exchange.getOut().getBody();
        }
    }

    public static void setIn(MinaEndpoint endpoint, Exchange exchange, Object payload) {
        if (isExchangeTransfer(endpoint, payload)) {
            DefaultExchangeHolder.unmarshal(exchange, (DefaultExchangeHolder) payload);
        } else {
            // normal transfer using the body only
            exchange.getIn().setBody(payload);
        }
    }

    public static void setOut(MinaEndpoint endpoint, Exchange exchange, Object payload) {
        if (isExchangeTransfer(endpoint, payload)) {
            DefaultExchangeHolder.unmarshal(exchange, (DefaultExchangeHolder) payload);
        } else {
            // normal transfer using the body only and preserve the headers
            exchange.getOut().setHeaders(exchange.getIn().getHeaders());
            exchange.getOut().setBody(payload);
        }
    }

    /**
     * A decoded {@link DefaultExchangeHolder} rebuilds the entire Exchange - id, body, headers, out message and every
     * property - so it is only honoured when the endpoint asked for exchange transfer in the first place. Without that
     * check any peer able to reach an endpoint using the object codec, which is the default when {@code textline} is
     * not set, could hand the route a whole Exchange of its choosing; the holder lives in the allow-listed
     * {@code org.apache.camel} namespace, so {@code objectCodecPattern} does not constrain it. This mirrors
     * {@code JmsBinding}, which likewise unmarshals a holder only on the opted-in path.
     */
    private static boolean isExchangeTransfer(MinaEndpoint endpoint, Object payload) {
        return payload instanceof DefaultExchangeHolder
                && endpoint != null && endpoint.getConfiguration().isTransferExchange();
    }

    /**
     * @deprecated          use {@link #setIn(MinaEndpoint, Exchange, Object)}. This overload has no endpoint and so
     *                      cannot check {@code transferExchange} before unmarshalling a {@link DefaultExchangeHolder}.
     * @param      exchange the exchange
     * @param      payload  the decoded payload
     */
    @Deprecated
    public static void setIn(Exchange exchange, Object payload) {
        setIn(null, exchange, payload);
    }

    /**
     * @deprecated          use {@link #setOut(MinaEndpoint, Exchange, Object)}. This overload has no endpoint and so
     *                      cannot check {@code transferExchange} before unmarshalling a {@link DefaultExchangeHolder}.
     * @param      exchange the exchange
     * @param      payload  the decoded payload
     */
    @Deprecated
    public static void setOut(Exchange exchange, Object payload) {
        setOut(null, exchange, payload);
    }
}
