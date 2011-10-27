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

import org.apache.camel.CamelContext;
import org.apache.felix.gogo.commands.Argument;
import org.apache.felix.gogo.commands.Command;
import org.apache.karaf.shell.console.OsgiCommandSupport;

/**
 * Command to start a Camel context.
 */
@Command(scope = "camel", name = "start-context", description = "Start a Camel context.")
public class StartContextCommand extends OsgiCommandSupport {

    @Argument(index = 0, name = "context", description = "The name of the Camel context.", required = true, multiValued = false)
    String context;

    private CamelController camelController;

    public void setCamelController(CamelController camelController) {
        this.camelController = camelController;
    }

    public Object doExecute() throws Exception {
        CamelContext camelContext = camelController.getCamelContext(context);
        if (camelContext == null) {
            System.err.println("Camel context " + context + " not found.");
            return null;
        }
        camelContext.start();
        return null;
    }

}
