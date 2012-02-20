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
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.camel.Consumer;
import org.apache.camel.Route;
import org.apache.camel.Service;
import org.apache.camel.spi.RouteStartupOrder;

/**
 * Default implementation of {@link org.apache.camel.spi.RouteStartupOrder}.
 *
 * @version 
 */
public class DefaultRouteStartupOrder implements RouteStartupOrder {

    private final int startupOrder;
    private final Route route;
    private final RouteService routeService;

    public DefaultRouteStartupOrder(int startupOrder, Route route, RouteService routeService) {
        this.startupOrder = startupOrder;
        this.route = route;
        this.routeService = routeService;
    }

    public int getStartupOrder() {
        return startupOrder;
    }

    public Route getRoute() {
        return route;
    }

    public List<Consumer> getInputs() {
        List<Consumer> answer = new ArrayList<Consumer>();
        Map<Route, Consumer> inputs = routeService.getInputs();
        for (Consumer consumer : inputs.values()) {
            answer.add(consumer);
        }
        return answer;
    }

    public List<Service> getServices() {
        List<Service> answer = new ArrayList<Service>();
        Collection<Route> routes = routeService.getRoutes();
        for (Route route : routes) {
            answer.addAll(route.getServices());
        }
        return answer;
    }

    public RouteService getRouteService() {
        return routeService;
    }

    @Override
    public String toString() {
        return "Route " + route.getId() + " starts in order " + startupOrder;
    }
}
