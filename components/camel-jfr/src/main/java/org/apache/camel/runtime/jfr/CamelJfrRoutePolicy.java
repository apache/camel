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
package org.apache.camel.runtime.jfr;

import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.support.RoutePolicySupport;

/**
 * Emits a {@link CamelRouteEvent} spanning the time an exchange spends in a route.
 *
 * @since 4.22
 */
public class CamelJfrRoutePolicy extends RoutePolicySupport {

    static final String PROP_ROUTE_STACK = "CamelJfrRouteStack";

    @Override
    public void onExchangeBegin(Route route, Exchange exchange) {
        CamelRouteEvent event = new CamelRouteEvent();
        if (event.isEnabled()) {
            event.routeId = route.getRouteId();
            event.exchangeId = exchange.getExchangeId();
            event.begin();
            JfrEventStack.push(exchange, PROP_ROUTE_STACK, event);
        }
    }

    @Override
    public void onExchangeDone(Route route, Exchange exchange) {
        CamelRouteEvent event = JfrEventStack.pop(exchange, PROP_ROUTE_STACK, CamelRouteEvent.class);
        if (event != null) {
            event.failed = exchange.isFailed();
            event.end();
            event.commit();
        }
    }
}
