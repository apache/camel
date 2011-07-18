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

import org.apache.camel.Route;
import org.apache.felix.gogo.commands.Argument;
import org.apache.felix.gogo.commands.Command;
import org.apache.karaf.shell.console.OsgiCommandSupport;

/**
 * Command to list all Camel routes.
 */
@Command(scope = "camel", name = "list-routes", description = "List all Camel routes.")
public class ListRoutesCommand extends OsgiCommandSupport {

    protected static final String OUTPUT_FORMAT = "[%-20s]";

    @Argument(index = 0, name = "name", description = "The Camel context name where to look for the route", required = false, multiValued = false)
    String name;

    private CamelController camelController;

    public void setCamelController(CamelController camelController) {
        this.camelController = camelController;
    }

    protected Object doExecute() throws Exception {
        List<Route> routes = camelController.getRoutes(name);
        for (Route route : routes) {
            System.out.println(String.format(OUTPUT_FORMAT, route.getId()));
        }
        return null;
    }

}
