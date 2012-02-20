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

import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.apache.camel.model.RouteDefinition;
import org.apache.felix.gogo.commands.Argument;
import org.apache.felix.gogo.commands.Command;
import org.apache.karaf.shell.console.OsgiCommandSupport;

/**
 * Command to start a route.
 */
@Command(scope = "camel", name = "route-start", description = "Start a Camel route.")
public class RouteStart extends OsgiCommandSupport {

    @Argument(index = 0, name = "route", description = "The Camel route ID.", required = true, multiValued = false)
    String route;

    @Argument(index = 1, name = "context", description = "The Camel context name.", required = false, multiValued = false)
    String context;

    private CamelController camelController;

    public void setCamelController(CamelController camelController) {
        this.camelController = camelController;
    }

    @SuppressWarnings("deprecation")
    public Object doExecute() throws Exception {
        Route camelRoute = camelController.getRoute(route, context);
        if (camelRoute == null) {
            List<CamelContext> camelContexts = camelController.getCamelContexts();
            for (CamelContext camelContext : camelContexts) {
                RouteDefinition routeDefinition = camelContext.getRouteDefinition(route);
                if (routeDefinition != null) {
                    camelContext.startRoute(routeDefinition.getId());
                    return null;
                }
            }
            System.err.println("Camel route " + route + " not found.");
            return null;
        } else {
            CamelContext camelContext = camelRoute.getRouteContext().getCamelContext();
            camelContext.startRoute(route);
        }
        return null;
    }

}
