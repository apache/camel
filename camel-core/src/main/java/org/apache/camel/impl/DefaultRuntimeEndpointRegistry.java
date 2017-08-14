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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.management.event.ExchangeCreatedEvent;
import org.apache.camel.management.event.ExchangeSendingEvent;
import org.apache.camel.management.event.RouteAddedEvent;
import org.apache.camel.management.event.RouteRemovedEvent;
import org.apache.camel.spi.EndpointUtilizationStatistics;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.spi.RuntimeEndpointRegistry;
import org.apache.camel.spi.UnitOfWork;
import org.apache.camel.support.EventNotifierSupport;
import org.apache.camel.util.LRUCacheFactory;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;

public class DefaultRuntimeEndpointRegistry extends EventNotifierSupport implements CamelContextAware, RuntimeEndpointRegistry {

    private CamelContext camelContext;

    // route id -> endpoint urls
    private Map<String, Set<String>> inputs;
    private Map<String, Map<String, String>> outputs;
    private int limit = 1000;
    private boolean enabled = true;
    private volatile boolean extended;
    private EndpointUtilizationStatistics inputUtilization;
    private EndpointUtilizationStatistics outputUtilization;

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

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
            for (Map.Entry<String, Set<String>> entry : inputs.entrySet()) {
                answer.addAll(entry.getValue());
            }
        }
        for (Map.Entry<String, Map<String, String>> entry : outputs.entrySet()) {
            answer.addAll(entry.getValue().keySet());
        }
        return Collections.unmodifiableList(answer);
    }

    @Override
    public List<String> getEndpointsPerRoute(String routeId, boolean includeInputs) {
        List<String> answer = new ArrayList<String>();
        if (includeInputs) {
            Set<String> uris = inputs.get(routeId);
            if (uris != null) {
                answer.addAll(uris);
            }
        }
        Map<String, String> uris = outputs.get(routeId);
        if (uris != null) {
            answer.addAll(uris.keySet());
        }
        return Collections.unmodifiableList(answer);
    }

    @Override
    public List<Statistic> getEndpointStatistics() {
        List<Statistic> answer = new ArrayList<Statistic>();

        // inputs
        for (Map.Entry<String, Set<String>> entry : inputs.entrySet()) {
            String routeId = entry.getKey();
            for (String uri : entry.getValue()) {
                Long hits = 0L;
                if (extended) {
                    String key = asUtilizationKey(routeId, uri);
                    if (key != null) {
                        hits = inputUtilization.getStatistics().get(key);
                        if (hits == null) {
                            hits = 0L;
                        }
                    }
                }
                answer.add(new EndpointRuntimeStatistics(uri, routeId, "in", hits));
            }
        }

        // outputs
        for (Map.Entry<String, Map<String, String>> entry : outputs.entrySet()) {
            String routeId = entry.getKey();
            for (String uri : entry.getValue().keySet()) {
                Long hits = 0L;
                if (extended) {
                    String key = asUtilizationKey(routeId, uri);
                    if (key != null) {
                        hits = outputUtilization.getStatistics().get(key);
                        if (hits == null) {
                            hits = 0L;
                        }
                    }
                }
                answer.add(new EndpointRuntimeStatistics(uri, routeId, "out", hits));
            }
        }

        return answer;
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
    public void clear() {
        inputs.clear();
        outputs.clear();
        reset();
    }

    @Override
    public void reset() {
        // its safe to call clear as reset
        if (inputUtilization != null) {
            inputUtilization.clear();
        }
        if (outputUtilization != null) {
            outputUtilization.clear();
        }
    }

    @Override
    public int size() {
        int total = inputs.values().size();
        total += outputs.values().size();
        return total;
    }

    @Override
    protected void doStart() throws Exception {
        ObjectHelper.notNull(camelContext, "camelContext", this);

        if (inputs == null) {
            inputs = new HashMap<String, Set<String>>();
        }
        if (outputs == null) {
            outputs = new HashMap<String, Map<String, String>>();
        }
        if (getCamelContext().getManagementStrategy().getManagementAgent() != null) {
            extended = getCamelContext().getManagementStrategy().getManagementAgent().getStatisticsLevel().isExtended();
        }
        if (extended) {
            inputUtilization = new DefaultEndpointUtilizationStatistics(limit);
            outputUtilization = new DefaultEndpointUtilizationStatistics(limit);
        }
        if (extended) {
            log.info("Runtime endpoint registry is in extended mode gathering usage statistics of all incoming and outgoing endpoints (cache limit: {})", limit);
        } else {
            log.info("Runtime endpoint registry is in normal mode gathering information of all incoming and outgoing endpoints (cache limit: {})", limit);
        }
        ServiceHelper.startServices(inputUtilization, outputUtilization);
    }

    @Override
    protected void doStop() throws Exception {
        clear();
        ServiceHelper.stopServices(inputUtilization, outputUtilization);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void notify(EventObject event) throws Exception {
        if (event instanceof RouteAddedEvent) {
            RouteAddedEvent rse = (RouteAddedEvent) event;
            Endpoint endpoint = rse.getRoute().getEndpoint();
            String routeId = rse.getRoute().getId();

            // a HashSet is fine for inputs as we only have a limited number of those
            Set<String> uris = new HashSet<String>();
            uris.add(endpoint.getEndpointUri());
            inputs.put(routeId, uris);
            // use a LRUCache for outputs as we could potential have unlimited uris if dynamic routing is in use
            // and therefore need to have the limit in use
            outputs.put(routeId, LRUCacheFactory.newLRUCache(limit));
        } else if (event instanceof RouteRemovedEvent) {
            RouteRemovedEvent rse = (RouteRemovedEvent) event;
            String routeId = rse.getRoute().getId();
            inputs.remove(routeId);
            outputs.remove(routeId);
            if (extended) {
                String uri = rse.getRoute().getEndpoint().getEndpointUri();
                String key = asUtilizationKey(routeId, uri);
                if (key != null) {
                    inputUtilization.remove(key);
                }
            }
        } else if (extended && event instanceof ExchangeCreatedEvent) {
            // we only capture details in extended mode
            ExchangeCreatedEvent ece = (ExchangeCreatedEvent) event;
            Endpoint endpoint = ece.getExchange().getFromEndpoint();
            if (endpoint != null) {
                String routeId = ece.getExchange().getFromRouteId();
                String uri = endpoint.getEndpointUri();
                String key = asUtilizationKey(routeId, uri);
                if (key != null) {
                    inputUtilization.onHit(key);
                }
            }
        } else if (event instanceof ExchangeSendingEvent) {
            ExchangeSendingEvent ese = (ExchangeSendingEvent) event;
            Endpoint endpoint = ese.getEndpoint();
            String routeId = getRouteId(ese.getExchange());
            String uri = endpoint.getEndpointUri();

            Map<String, String> uris = outputs.get(routeId);
            if (uris != null && !uris.containsKey(uri)) {
                uris.put(uri, uri);
            }
            if (extended) {
                String key = asUtilizationKey(routeId, uri);
                if (key != null) {
                    outputUtilization.onHit(key);
                }
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
    public boolean isDisabled() {
        return !enabled;
    }

    @Override
    public boolean isEnabled(EventObject event) {
        return enabled && event instanceof ExchangeCreatedEvent
                || event instanceof ExchangeSendingEvent
                || event instanceof RouteAddedEvent
                || event instanceof RouteRemovedEvent;
    }

    private static String asUtilizationKey(String routeId, String uri) {
        if (routeId == null || uri == null) {
            return null;
        } else {
            return routeId + "|" + uri;
        }
    }

    private static final class EndpointRuntimeStatistics implements Statistic {

        private final String uri;
        private final String routeId;
        private final String direction;
        private final long hits;

        private EndpointRuntimeStatistics(String uri, String routeId, String direction, long hits) {
            this.uri = uri;
            this.routeId = routeId;
            this.direction = direction;
            this.hits = hits;
        }

        public String getUri() {
            return uri;
        }

        public String getRouteId() {
            return routeId;
        }

        public String getDirection() {
            return direction;
        }

        public long getHits() {
            return hits;
        }
    }
}
