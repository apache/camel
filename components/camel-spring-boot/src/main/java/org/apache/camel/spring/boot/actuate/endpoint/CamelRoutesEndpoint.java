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
package org.apache.camel.spring.boot.actuate.endpoint;

import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.apache.camel.StatefulService;
import org.apache.camel.spring.boot.actuate.endpoint.CamelRoutesEndpoint.RouteEndpointInfo;
import org.springframework.boot.actuate.endpoint.AbstractEndpoint;
import org.springframework.boot.actuate.endpoint.Endpoint;

/**
 * {@link Endpoint} to expose {@link org.apache.camel.Route} information.
 */
public class CamelRoutesEndpoint extends AbstractEndpoint<List<RouteEndpointInfo>> {

    private static final String ENDPOINT_ID = "camelroutes";

    private CamelContext camelContext;

    public CamelRoutesEndpoint(CamelContext camelContext) {
        super(ENDPOINT_ID);
        this.camelContext = camelContext;
    }

    @Override
    public List<RouteEndpointInfo> invoke() {
        // @formatter:off
        return camelContext.getRoutes().stream()
                .map(RouteEndpointInfo::new)
                .collect(Collectors.toList());
        // @formatter:on
    }

    /**
     * Container for exposing {@link org.apache.camel.Route} information as JSON.
     */
    @JsonPropertyOrder({"id", "description", "uptime", "uptimeMillis"})
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public static class RouteEndpointInfo {

        private final String id;

        private final String description;

        private final String uptime;

        private final long uptimeMillis;

        private String status;

        public RouteEndpointInfo(Route route) {
            this.id = route.getId();
            this.description = route.getDescription();
            this.uptime = route.getUptime();
            this.uptimeMillis = route.getUptimeMillis();

            if (route instanceof StatefulService) {
                this.status = ((StatefulService) route).getStatus().name();
            }
        }

        public String getId() {
            return id;
        }

        public String getDescription() {
            return description;
        }

        public String getUptime() {
            return uptime;
        }

        public long getUptimeMillis() {
            return uptimeMillis;
        }

        public String getStatus() {
            return status;
        }
    }

}
