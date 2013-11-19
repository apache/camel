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
package org.apache.camel.karaf.commands.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Route;
import org.apache.camel.karaf.commands.CamelController;
import org.apache.camel.model.RouteDefinition;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of <code>CamelController</code>.
 */
public class CamelControllerImpl implements CamelController {

    private static final Logger LOG = LoggerFactory.getLogger(CamelControllerImpl.class);

    private BundleContext bundleContext;

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public List<CamelContext> getCamelContexts() {
        List<CamelContext> camelContexts = new ArrayList<CamelContext>();
        try {
            ServiceReference<?>[] references = bundleContext.getServiceReferences(CamelContext.class.getName(), null);
            if (references != null) {
                for (ServiceReference<?> reference : references) {
                    if (reference != null) {
                        CamelContext camelContext = (CamelContext) bundleContext.getService(reference);
                        if (camelContext != null) {
                            camelContexts.add(camelContext);
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOG.warn("Cannot retrieve the list of Camel contexts.", e);
        }

        // sort the list
        Collections.sort(camelContexts, new Comparator<CamelContext>() {
            @Override
            public int compare(CamelContext o1, CamelContext o2) {
                return o1.getName().compareTo(o2.getName());
            }
        });

        return camelContexts;
    }

    public CamelContext getCamelContext(String name) {
        for (CamelContext camelContext : this.getCamelContexts()) {
            if (camelContext.getName().equals(name)) {
                return camelContext;
            }
        }
        return null;
    }

    public List<Route> getRoutes(String camelContextName) {
        return getRoutes(camelContextName, null);
    }

    public List<Route> getRoutes(String camelContextName, String filter) {
        List<Route> routes = new ArrayList<Route>();

        if (camelContextName != null) {
            CamelContext context = this.getCamelContext(camelContextName);
            if (context != null) {
                for (Route route : context.getRoutes()) {
                    if (filter == null || route.getId().matches(filter)) {
                        routes.add(route);
                    }
                }
            }
        } else {
            List<CamelContext> camelContexts = this.getCamelContexts();
            for (CamelContext camelContext : camelContexts) {
                for (Route route : camelContext.getRoutes()) {
                    if (filter == null || route.getId().matches(filter)) {
                        routes.add(route);
                    }
                }
            }
        }

        // sort the list
        Collections.sort(routes, new Comparator<Route>() {
            @Override
            public int compare(Route o1, Route o2) {
                // group by camel context first, then by route name
                String c1 = o1.getRouteContext().getCamelContext().getName();
                String c2 = o2.getRouteContext().getCamelContext().getName();

                int answer = c1.compareTo(c2);
                if (answer == 0) {
                    // okay from same camel context, then sort by route id
                    answer = o1.getId().compareTo(o2.getId());
                }
                return answer;
            }
        });
        return routes;
    }

    @SuppressWarnings("deprecation")
    public List<RouteDefinition> getRouteDefinitions(String camelContextName) {
        List<RouteDefinition> answer = new ArrayList<RouteDefinition>();

        if (camelContextName != null) {
            CamelContext context = this.getCamelContext(camelContextName);
            if (context != null) {
                List<RouteDefinition> routes = context.getRouteDefinitions();
                // sort routes
                Collections.sort(routes, new Comparator<RouteDefinition>() {
                    @Override
                    public int compare(RouteDefinition o1, RouteDefinition o2) {
                        // already sorted by camel context first, so just sort the ids
                        return o1.getId().compareTo(o2.getId());
                    }
                });
                answer.addAll(routes);
            }
        } else {
            // already sorted by camel context
            List<CamelContext> camelContexts = this.getCamelContexts();
            for (CamelContext camelContext : camelContexts) {
                for (RouteDefinition routeDefinition : camelContext.getRouteDefinitions()) {
                    List<RouteDefinition> routes = new ArrayList<RouteDefinition>();
                    // sort routes
                    Collections.sort(routes, new Comparator<RouteDefinition>() {
                        @Override
                        public int compare(RouteDefinition o1, RouteDefinition o2) {
                            // already sorted by camel context first, so just sort the ids
                            return o1.getId().compareTo(o2.getId());
                        }
                    });
                    answer.addAll(routes);
                }
            }
        }
        // already sorted
        return answer;
    }

    public Route getRoute(String routeId, String camelContextName) {
        List<Route> routes = this.getRoutes(camelContextName);
        for (Route route : routes) {
            if (route.getId().equals(routeId)) {
                return route;
            }
        }
        return null;
    }

    @SuppressWarnings("deprecation")
    public RouteDefinition getRouteDefinition(String routeId, String camelContextName) {
        CamelContext context = this.getCamelContext(camelContextName);
        if (context == null) {
            return null;
        }
        return context.getRouteDefinition(routeId);
    }

    public List<Endpoint> getEndpoints(String camelContextName) {
        List<Endpoint> answer = new ArrayList<Endpoint>();

        if (camelContextName != null) {
            CamelContext context = this.getCamelContext(camelContextName);
            if (context != null) {
                List<Endpoint> endpoints = new ArrayList<Endpoint>(context.getEndpoints());
                // sort routes
                Collections.sort(endpoints, new Comparator<Endpoint>() {
                    @Override
                    public int compare(Endpoint o1, Endpoint o2) {
                        return o1.getEndpointKey().compareTo(o2.getEndpointKey());
                    }
                });
                answer.addAll(endpoints);
            }
        } else {
            // already sorted by camel context
            List<CamelContext> camelContexts = this.getCamelContexts();
            for (CamelContext camelContext : camelContexts) {
                List<Endpoint> endpoints = new ArrayList<Endpoint>(camelContext.getEndpoints());
                // sort routes
                Collections.sort(endpoints, new Comparator<Endpoint>() {
                    @Override
                    public int compare(Endpoint o1, Endpoint o2) {
                        return o1.getEndpointKey().compareTo(o2.getEndpointKey());
                    }
                });
                answer.addAll(endpoints);
            }
        }
        // already sorted
        return answer;
    }
}
