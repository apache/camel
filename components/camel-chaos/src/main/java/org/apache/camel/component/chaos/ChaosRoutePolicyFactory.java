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
package org.apache.camel.component.chaos;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.NamedNode;
import org.apache.camel.Route;
import org.apache.camel.component.chaos.ChaosRule.Hook;
import org.apache.camel.spi.RoutePolicy;
import org.apache.camel.spi.RoutePolicyFactory;
import org.apache.camel.support.RoutePolicySupport;
import org.apache.camel.util.TimeSource;

/**
 * Gives every route a route policy that raises the {@code corrupt} faults of the {@code route=} rules on the messages
 * that enter the route, and counts for the report the exchanges that complete or fail in the route.
 */
final class ChaosRoutePolicyFactory implements RoutePolicyFactory {

    private final ChaosManager manager;

    ChaosRoutePolicyFactory(ChaosManager manager) {
        this.manager = manager;
    }

    @Override
    public RoutePolicy createRoutePolicy(CamelContext camelContext, String routeId, NamedNode route) {
        return new ChaosRoutePolicy(manager);
    }

    /**
     * Changes the message of an exchange as it enters the route ({@code onExchangeBegin}); the first rule that fires
     * decides the fault. Counts the exchange when the route is done with it ({@code onExchangeDone}), while there are
     * rules.
     */
    static final class ChaosRoutePolicy extends RoutePolicySupport {

        private final ChaosManager manager;

        ChaosRoutePolicy(ChaosManager manager) {
            this.manager = manager;
        }

        @Override
        public void onExchangeBegin(Route route, Exchange exchange) {
            ChaosRule[] rules = manager.ruleSet().entry;
            if (rules.length == 0) {
                return;
            }
            long now = TimeSource.currentTimeMillis();
            for (ChaosRule rule : rules) {
                if (rule.live(now) && rule.matchesId(route.getRouteId()) && rule.fires(now)) {
                    ChaosFaults.corrupt(rule, exchange, Hook.ENTRY);
                    return;
                }
            }
        }

        @Override
        public void onExchangeDone(Route route, Exchange exchange) {
            if (manager.isCounting()) {
                manager.routeDone(route, exchange);
            }
        }

        @Override
        public String toString() {
            return "ChaosRoutePolicy";
        }
    }
}
