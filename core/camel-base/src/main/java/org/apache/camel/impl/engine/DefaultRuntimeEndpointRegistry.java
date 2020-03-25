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
package org.apache.camel.impl.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.CamelEvent.ExchangeCreatedEvent;
import org.apache.camel.spi.CamelEvent.ExchangeSendingEvent;
import org.apache.camel.spi.CamelEvent.RouteAddedEvent;
import org.apache.camel.spi.CamelEvent.RouteRemovedEvent;
import org.apache.camel.spi.EndpointUtilizationStatistics;
import org.apache.camel.spi.RuntimeEndpointRegistry;
import org.apache.camel.support.EventNotifierSupport;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.LRUCacheFactory;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultRuntimeEndpointRegistry extends EventNotifierSupport implements CamelContextAware, RuntimeEndpointRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultRuntimeEndpointRegistry.class);

    private CamelContext camelContext;

    // route id -> endpoint urls
    private Map<String, Set<String>> inputs;
    private Map<String, Map<String, String>> outputs;
    private int limit = 1000;
    private boolean enabled = true;
    private volatile boolean extended;
    private EndpointUtilizationStatistics inputUtilization;
    private EndpointUtilizationStatistics outputUtilization;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public List<String> getAllEndpoints(boolean includeInputs) {
        List<String> answer = new ArrayList<>();
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
        List<String> answer = new ArrayList<>();
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
        List<Statistic> answer = new ArrayList<>();

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
    protected void doInit() throws Exception {
        ObjectHelper.notNull(camelContext, "camelContext", this);

        if (inputs == null) {
            inputs = new HashMap<>();
        }
        if (outputs == null) {
            outputs = new HashMap<>();
        }
        if (getCamelContext().getManagementStrategy() != null && getCamelContext().getManagementStrategy().getManagementAgent() != null) {
            extended = getCamelContext().getManagementStrategy().getManagementAgent().getStatisticsLevel().isExtended();
        }
        if (extended) {
            inputUtilization = new DefaultEndpointUtilizationStatistics(limit);
            outputUtilization = new DefaultEndpointUtilizationStatistics(limit);
        }
        if (extended) {
            LOG.info("Runtime endpoint registry is in extended mode gathering usage statistics of all incoming and outgoing endpoints (cache limit: {})", limit);
        } else {
            LOG.info("Runtime endpoint registry is in normal mode gathering information of all incoming and outgoing endpoints (cache limit: {})", limit);
        }
        ServiceHelper.initService(inputUtilization, outputUtilization);
    }

    @Override
    protected void doStart() throws Exception {
        ServiceHelper.startService(inputUtilization, outputUtilization);
    }

    @Override
    protected void doStop() throws Exception {
        clear();
        ServiceHelper.stopService(inputUtilization, outputUtilization);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void notify(CamelEvent event) throws Exception {
        if (event instanceof RouteAddedEvent) {
            RouteAddedEvent rse = (RouteAddedEvent) event;
            Endpoint endpoint = rse.getRoute().getEndpoint();
            String routeId = rse.getRoute().getId();

            // a HashSet is fine for inputs as we only have a limited number of those
            Set<String> uris = new HashSet<>();
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
            String routeId = ExchangeHelper.getRouteId(ese.getExchange());
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

    @Override
    public boolean isDisabled() {
        return !enabled;
    }

    @Override
    public boolean isEnabled(CamelEvent event) {
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

        @Override
        public String getUri() {
            return uri;
        }

        @Override
        public String getRouteId() {
            return routeId;
        }

        @Override
        public String getDirection() {
            return direction;
        }

        @Override
        public long getHits() {
            return hits;
        }
    }
}
