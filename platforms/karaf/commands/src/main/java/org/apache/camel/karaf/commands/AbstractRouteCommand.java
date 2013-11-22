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
package org.apache.camel.karaf.commands;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.apache.camel.karaf.commands.internal.RegexUtil;
import org.apache.felix.gogo.commands.Argument;

import static org.apache.camel.util.CamelContextHelper.getRouteStartupOrder;

public abstract class AbstractRouteCommand extends CamelCommandSupport {
    @Argument(index = 0, name = "route", description = "The Camel route ID or a wildcard expression", required = true, multiValued = false)
    String route;

    @Argument(index = 1, name = "context", description = "The Camel context name.", required = false, multiValued = false)
    String context;

    public abstract void executeOnRoute(CamelContext camelContext, Route camelRoute) throws Exception;

    public Object doExecute() throws Exception {
        List<Route> camelRoutes = camelController.getRoutes(context, RegexUtil.wildcardAsRegex(route));
        if (camelRoutes == null || camelRoutes.isEmpty()) {
            System.err.println("Camel routes using " + route + " not found.");
            return null;
        }
        // we want the routes sorted
        Collections.sort(camelRoutes, new RouteComparator());

        for (Route camelRoute : camelRoutes) {
            CamelContext camelContext = camelRoute.getRouteContext().getCamelContext();
            // Setting thread context classloader to the bundle classloader to enable
            // legacy code that relies on it
            ClassLoader oldClassloader = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(camelContext.getApplicationContextClassLoader());
            try {
                executeOnRoute(camelContext, camelRoute);
            } finally {
                Thread.currentThread().setContextClassLoader(oldClassloader);
            }
        }

        return null;
    }

    /**
     * To sort the routes.
     */
    private static final class RouteComparator implements Comparator<Route> {

        @Override
        public int compare(Route route1, Route route2) {
            // sort by camel context first
            CamelContext camel1 = route1.getRouteContext().getCamelContext();
            CamelContext camel2 = route2.getRouteContext().getCamelContext();

            if (camel1.getName().equals(camel2.getName())) {
                // and then accordingly to startup order
                Integer order1 = getRouteStartupOrder(camel1, route1.getId());
                Integer order2 = getRouteStartupOrder(camel2, route2.getId());
                if (order1 == 0 && order2 == 0) {
                    // fallback and use name if not startup order was found
                    return route1.getId().compareTo(route2.getId());
                } else {
                    return order1.compareTo(order2);
                }
            } else {
                return camel1.getName().compareTo(camel2.getName());
            }
        }
    }

}
