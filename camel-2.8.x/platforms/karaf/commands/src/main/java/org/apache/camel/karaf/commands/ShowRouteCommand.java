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

import org.apache.camel.Route;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.util.ModelHelper;
import org.apache.felix.gogo.commands.Argument;
import org.apache.felix.gogo.commands.Command;
import org.apache.karaf.shell.console.OsgiCommandSupport;

/**
 * Command to show the route marshaled in XML.
 */
@Command(scope = "camel", name = "show-route", description = "Display the Camel route definition in XML.")
public class ShowRouteCommand extends OsgiCommandSupport {

    @Argument(index = 0, name = "route", description = "The Camel route ID.", required = true, multiValued = false)
    String route;

    @Argument(index = 1, name = "context", description = "The Camel context name.", required = false, multiValued = false)
    String context;

    private CamelController camelController;

    public void setCamelController(CamelController camelController) {
        this.camelController = camelController;
    }

    public Object doExecute() throws Exception {
        Route camelRoute = camelController.getRoute(route, context);
        if (camelRoute == null) {
            System.err.println("Camel route " + route + " not found.");
            return null;
        }
        RouteDefinition routeDefinition = camelController.getRouteDefinition(route, camelRoute.getRouteContext().getCamelContext().getName());
        if (routeDefinition == null) {
            System.err.println("Definition of route " + route + " not found.");
            return null;
        }
        System.out.println(ModelHelper.dumpModelAsXml(routeDefinition));
        return null;
    }

}
