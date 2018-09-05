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
package org.apache.camel.commands;

import java.io.PrintStream;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.commands.internal.RegexUtil;

/**
 * Abstract command for working with a one ore more routes.
 */
public abstract class AbstractRouteCommand extends AbstractCamelCommand {

    private String route;
    private String context;

    /**
     * @param route The Camel route ID or a wildcard expression
     * @param context The name of the Camel context.
     */
    protected AbstractRouteCommand(String route, String context) {
        this.route = route;
        this.context = context;
    }

    public Object execute(CamelController camelController, PrintStream out, PrintStream err) throws Exception {
        List<Map<String, String>> camelRoutes = camelController.getRoutes(context, RegexUtil.wildcardAsRegex(route));
        if (camelRoutes == null || camelRoutes.isEmpty()) {
            err.println("Camel routes using " + route + " not found.");
            return null;
        }
        // we want the routes sorted
        Collections.sort(camelRoutes, new RouteComparator());

        for (Map<String, String> row : camelRoutes) {
            String camelContextName = row.get("camelContextName");
            String routeId = row.get("routeId");
            if (camelController instanceof LocalCamelController) {
                executeLocal((LocalCamelController) camelController, camelContextName, routeId, out, err);
            } else {
                executeOnRoute(camelController, camelContextName, routeId, out, err);
            }
        }

        return null;
    }

    private void executeLocal(LocalCamelController camelController, String camelContextName, String routeId, PrintStream out, PrintStream err) throws Exception {
        CamelContext camelContext = camelController.getLocalCamelContext(context);
        if (camelContext == null) {
            err.println("Camel context " + context + " not found.");
            return;
        }

        // Setting thread context classloader to the bundle classloader to enable legacy code that relies on it
        ClassLoader oldClassloader = Thread.currentThread().getContextClassLoader();
        ClassLoader applicationContextClassLoader = camelContext.getApplicationContextClassLoader();
        if (applicationContextClassLoader  != null) {
            Thread.currentThread().setContextClassLoader(applicationContextClassLoader);
        }
        try {
            executeOnRoute(camelController, camelContextName, routeId, out, err);
        } finally {
            Thread.currentThread().setContextClassLoader(oldClassloader);
        }
    }

    public abstract void executeOnRoute(CamelController camelController, String contextName, String routeId, PrintStream out, PrintStream err) throws Exception;

    /**
     * To sort the routes.
     */
    private static final class RouteComparator implements Comparator<Map<String, String>> {

        @Override
        public int compare(Map<String, String> route1, Map<String, String> route2) {
            // sort by camel context first
            String camel1 = route1.get("camelContextName");
            String camel2 = route2.get("camelContextName");

            if (camel1.equals(camel2)) {
                return route1.get("routeId").compareTo(route2.get("routeId"));
            } else {
                return camel1.compareTo(camel2);
            }
        }
    }

}
