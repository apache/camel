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
package org.apache.camel.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EventObject;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.management.event.ExchangeSendingEvent;
import org.apache.camel.management.event.RouteStartedEvent;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.spi.RuntimeEndpointRegistry;
import org.apache.camel.spi.UnitOfWork;
import org.apache.camel.support.EventNotifierSupport;
import org.apache.camel.util.LRUCache;

public class DefaultRuntimeEndpointRegistry extends EventNotifierSupport implements RuntimeEndpointRegistry {

    // endpoint uri -> route ids
    private Map<String, Set<String>> inputs;
    private Map<String, Set<String>> outputs;
    private int limit = 1000;
    private boolean enabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public List<String> getAllEndpoints(boolean includeInputs) {
        List<String> answer = new ArrayList<String>();
        if (includeInputs) {
            answer.addAll(inputs.keySet());
        }
        answer.addAll(outputs.keySet());
        return Collections.unmodifiableList(answer);
    }

    @Override
    public List<String> getEndpointsPerRoute(String routeId, boolean includeInputs) {
        List<String> answer = new ArrayList<String>();
        if (includeInputs) {
            for (Map.Entry<String, Set<String>> entry : inputs.entrySet()) {
                if (entry.getValue().contains(routeId)) {
                    answer.add(entry.getKey());
                }
            }
        }
        for (Map.Entry<String, Set<String>> entry : outputs.entrySet()) {
            if (entry.getValue().contains(routeId)) {
                answer.add(entry.getKey());
            }
        }
        return Collections.unmodifiableList(answer);
    }

    @Override
    public int getLimit() {
        return limit;
    }

    @Override
    public void setLimit(int limit) {
        this.limit = limit;
    }

    @Override
    public void reset() {
        inputs.clear();
        outputs.clear();
    }

    @Override
    public int size() {
        int total = inputs.size();
        total += outputs.size();
        return total;
    }

    @Override
    protected void doStart() throws Exception {
        if (inputs == null) {
            inputs = new LRUCache<String, Set<String>>(limit);
        }
        if (outputs == null) {
            outputs = new LRUCache<String, Set<String>>(limit);
        }
    }

    @Override
    protected void doStop() throws Exception {
        reset();
    }

    @Override
    public void notify(EventObject event) throws Exception {
        if (event instanceof RouteStartedEvent) {
            RouteStartedEvent rse = (RouteStartedEvent) event;
            Endpoint endpoint = rse.getRoute().getEndpoint();
            String routeId = rse.getRoute().getId();

            Set<String> routes = inputs.get(endpoint);
            if (routeId != null && (routes == null || !routes.contains(routeId))) {
                if (routes == null) {
                    routes = new ConcurrentSkipListSet<String>();
                }
                routes.add(routeId);
                inputs.put(endpoint.getEndpointUri(), routes);
            }
        } else {
            ExchangeSendingEvent ese = (ExchangeSendingEvent) event;
            Endpoint endpoint = ese.getEndpoint();
            String routeId = getRouteId(ese.getExchange());

            Set<String> routes = outputs.get(endpoint);
            if (routeId != null && (routes == null || !routes.contains(routeId))) {
                if (routes == null) {
                    routes = new ConcurrentSkipListSet<String>();
                }
                routes.add(routeId);
                outputs.put(endpoint.getEndpointUri(), routes);
            }
        }
    }

    private String getRouteId(Exchange exchange) {
        String answer = null;
        UnitOfWork uow = exchange.getUnitOfWork();
        RouteContext rc = uow != null ? uow.getRouteContext() : null;
        if (rc != null) {
            answer = rc.getRoute().getId();
        }
        if (answer == null) {
            // fallback and get from route id on the exchange
            answer = exchange.getFromRouteId();
        }
        return answer;
    }

    @Override
    public boolean isEnabled(EventObject event) {
        return enabled && event instanceof ExchangeSendingEvent
                || event instanceof RouteStartedEvent;
    }
}
