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
package org.apache.camel.component.undertow;

import io.undertow.util.HttpString;
import org.apache.camel.Exchange;
import org.apache.camel.http.base.HttpHeaderFilterStrategy;

public class UndertowHeaderFilterStrategy extends HttpHeaderFilterStrategy {

    /**
     * Legacy {@code websocket.*} Exchange-header prefix used by {@code UndertowConstants} for the dispatch and event
     * headers ({@code websocket.connectionKey}, {@code websocket.connectionKey.list}, {@code websocket.sendToAll},
     * {@code websocket.eventType}, {@code websocket.eventTypeEnum}, {@code websocket.channel},
     * {@code websocket.exchange}). Added to the in/out filter prefixes (CAMEL-23588) so the undertow boundary does not
     * propagate these values onto outbound wire frames or map them in from inbound HTTP-style headers. This is
     * defence-in-depth — cross-component routes that flow an untrusted message into an undertow producer should also
     * {@code .removeHeaders("websocket.*")} at the trust boundary, because the producer reads these headers via
     * {@code in.getHeader(...)} which bypasses the {@code HeaderFilterStrategy}.
     */
    static final String WEBSOCKET_FILTER_STARTS_WITH = "websocket.";

    public UndertowHeaderFilterStrategy() {
        initialize();
    }

    @Override
    protected void initialize() {
        super.initialize();
        setOutFilterStartsWith("Camel", "camel", WEBSOCKET_FILTER_STARTS_WITH);
        setInFilterStartsWith("Camel", "camel", WEBSOCKET_FILTER_STARTS_WITH);
    }

    @Override
    public boolean applyFilterToExternalHeaders(String headerName, Object headerValue, Exchange exchange) {
        boolean skip = HttpString.tryFromString(headerName) == null;
        if (skip) {
            // skip all not valid headers by undertow rules
            return true;
        }
        return super.applyFilterToExternalHeaders(headerName, headerValue, exchange);
    }
}
