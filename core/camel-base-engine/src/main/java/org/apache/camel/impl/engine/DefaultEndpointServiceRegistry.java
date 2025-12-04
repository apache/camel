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
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Route;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.EndpointServiceRegistry;
import org.apache.camel.spi.RuntimeEndpointRegistry;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.service.ServiceSupport;

public class DefaultEndpointServiceRegistry extends ServiceSupport implements EndpointServiceRegistry {

    private final CamelContext camelContext;

    public DefaultEndpointServiceRegistry(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public List<EndpointService> listAllEndpointServices() {
        List<EndpointService> answer = new ArrayList<>();

        // find all consumers (IN) direction
        for (Route route : camelContext.getRoutes()) {
            Consumer consumer = route.getConsumer();
            Endpoint endpoint = consumer.getEndpoint();
            if (endpoint instanceof EndpointServiceLocation esl) {
                EndpointService es = newEndpointService(endpoint, consumer, "in", esl);
                if (es != null) {
                    answer.add(es);
                }
            }
        }
        // find all endpoint (OUT) direction
        for (Endpoint endpoint : camelContext.getEndpointRegistry().getReadOnlyValues()) {
            if (endpoint instanceof EndpointServiceLocation esl) {
                // (platform-http is only IN)
                String component = endpoint.getComponent().getDefaultName();
                boolean skip = "platform-http".equals(component);
                if (!skip) {
                    EndpointService es = newEndpointService(endpoint, null, "out", esl);
                    if (es != null) {
                        answer.add(es);
                    }
                }
            }
        }

        return answer;
    }

    private EndpointService newEndpointService(
            Endpoint endpoint, Consumer consumer, String dir, EndpointServiceLocation esl) {
        EndpointService answer = null;
        String adr = esl.getServiceUrl();
        if (adr != null) {
            String component = endpoint.getComponent().getDefaultName();
            boolean hosted = false;
            String routeId = null;
            if (consumer instanceof DefaultConsumer dc) {
                hosted = dc.isHostedService();
                routeId = dc.getRouteId();
            }
            var stat = findStats(endpoint.getEndpointUri(), dir);
            long hits = 0;
            if (stat.isPresent()) {
                var s = stat.get();
                hits = s.getHits();
                routeId = s.getRouteId();
            }
            if ("out".equals(dir) && stat.isEmpty()) {
                // no OUT stat, then the endpoint may be used only for IN
                stat = findStats(endpoint.getEndpointUri(), "in");
                if (stat.isPresent()) {
                    return null;
                }
            }
            answer = new DefaultEndpointService(
                    component,
                    endpoint.getEndpointUri(),
                    adr,
                    esl.getServiceProtocol(),
                    esl.getServiceMetadata(),
                    hosted,
                    dir,
                    hits,
                    routeId);
        }
        return answer;
    }

    @Override
    public int size() {
        int size = 0;
        for (Endpoint e : camelContext.getEndpoints()) {
            if (e instanceof EndpointServiceLocation) {
                size++;
            }
        }
        return size;
    }

    private Optional<RuntimeEndpointRegistry.Statistic> findStats(String uri, String direction) {
        if (camelContext.getRuntimeEndpointRegistry() == null) {
            return Optional.empty();
        }
        return camelContext.getRuntimeEndpointRegistry().getEndpointStatistics().stream()
                .filter(s -> uri.equals(s.getUri())
                        && (direction == null || s.getDirection().equals(direction)))
                .findFirst();
    }

    private static final class DefaultEndpointService implements EndpointService {
        private final String component;
        private final String endpointUri;
        private final String serviceUrl;
        private final String serviceProtocol;
        private final Map<String, String> serviceMetadata;
        private final boolean hostedService;
        private final String direction;
        private final long hits;
        private final String routeId;

        public DefaultEndpointService(
                String component,
                String endpointUri,
                String serviceUrl,
                String serviceProtocol,
                Map<String, String> serviceMetadata,
                boolean hostedService,
                String direction,
                long hits,
                String routeId) {
            this.component = component;
            this.endpointUri = endpointUri;
            this.serviceUrl = serviceUrl;
            this.serviceProtocol = serviceProtocol;
            this.serviceMetadata = serviceMetadata;
            this.hostedService = hostedService;
            this.direction = direction;
            this.hits = hits;
            this.routeId = routeId;
        }

        @Override
        public String getComponent() {
            return component;
        }

        @Override
        public String getEndpointUri() {
            return endpointUri;
        }

        @Override
        public String getServiceUrl() {
            return serviceUrl;
        }

        @Override
        public String getServiceProtocol() {
            return serviceProtocol;
        }

        @Override
        public Map<String, String> getServiceMetadata() {
            return serviceMetadata;
        }

        @Override
        public boolean isHostedService() {
            return hostedService;
        }

        @Override
        public String getDirection() {
            return direction;
        }

        @Override
        public long getHits() {
            return hits;
        }

        @Override
        public String getRouteId() {
            return routeId;
        }
    }
}
