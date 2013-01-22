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
import org.apache.camel.karaf.commands.internal.RegexUtil;
import org.apache.felix.gogo.commands.Argument;
import org.apache.karaf.shell.console.OsgiCommandSupport;

public abstract class AbstractRouteCommand extends OsgiCommandSupport {
    @Argument(index = 0, name = "route", description = "The Camel route ID or a wildcard expression", required = true, multiValued = false)
    String route;

    @Argument(index = 1, name = "context", description = "The Camel context name.", required = false, multiValued = false)
    String context;

    private CamelController camelController;

    public void setCamelController(CamelController camelController) {
        this.camelController = camelController;
    }
    
    public abstract void executeOnRoute(CamelContext camelContext, Route camelRoute) throws Exception;
    
    public Object doExecute() throws Exception {
        List<Route> camelRoutes = camelController.getRoutes(context, RegexUtil.wildcardAsRegex(route));
        if (camelRoutes == null || camelRoutes.isEmpty()) {
            System.err.println("Camel routes using " + route + " not found.");
            return null;
        }
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
}
