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
import org.apache.felix.gogo.commands.Command;
import org.apache.karaf.shell.console.OsgiCommandSupport;

/**
 * List the Camel contexts available in the Karaf instance.
 */
@Command(scope = "camel", name = "list-contexts", description = "Lists all Camel contexts.")
public class ListContextsCommand extends OsgiCommandSupport {

    protected static final String HEADER_FORMAT = "%-20s %-20s %-20s";
    protected static final String OUTPUT_FORMAT = "[%-18s] [%-18s] [%-18s]";

    private CamelController camelController;

    public void setCamelController(CamelController camelController) {
        this.camelController = camelController;
    }

    protected Object doExecute() throws Exception {
        System.out.println(String.format(HEADER_FORMAT, "Name", "Status", "Uptime"));

        List<CamelContext> camelContexts = camelController.getCamelContexts();
        for (CamelContext camelContext : camelContexts) {
            System.out.println(String.format(OUTPUT_FORMAT, camelContext.getName(), camelContext.getStatus(), camelContext.getUptime()));
        }

        return null;
    }

}
