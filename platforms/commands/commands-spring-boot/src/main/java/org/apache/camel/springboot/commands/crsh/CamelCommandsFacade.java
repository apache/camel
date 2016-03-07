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
package org.apache.camel.springboot.commands.crsh;

import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;

import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.apache.camel.commands.AbstractCamelCommand;
import org.apache.camel.commands.AbstractContextCommand;
import org.apache.camel.commands.AbstractRouteCommand;
import org.apache.camel.commands.LocalCamelController;
import org.apache.camel.commands.StringEscape;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CamelCommandsFacade {

    protected final Logger log = LoggerFactory.getLogger(getClass().getName());

    private LocalCamelController camelController;
    private StringEscape stringEscape = new NoopStringEscape();

    CamelCommandsFacade(LocalCamelController controller) {
        this.camelController = controller;
    }

    LocalCamelController getCamelController() {
        return this.camelController;
    }

    public <T extends AbstractCamelCommand> String runCommand(Class<T> clazz, Object... commandArgs) throws Exception {
        OutputBuffer buffer = new OutputBuffer();
        PrintStream ops = buffer.getPrintStream();

        // Trying to infer the camel context if not given
        // The order of the varargs for Route Command
        // [0] - route id
        // [1] - camel context
        if (AbstractRouteCommand.class.isAssignableFrom(clazz) && null == commandArgs[1]) {
            commandArgs[1] = getCamelContextForRoute((String) commandArgs[0]);
            ops.println("Automatically inferred context name : " + commandArgs[1]);
        }

        // The order of the varargs for Context Command
        // [0] - camel context
        if (AbstractContextCommand.class.isAssignableFrom(clazz) && null == commandArgs[0]) {
            commandArgs[0] = getFirstCamelContextName();
            ops.println("Context name is not provided. Using the first : " + commandArgs[0]);
        }

        // Finding the right constructor
        Class[] types = new Class[commandArgs.length];
        for (int i = 0; i < commandArgs.length; i++) {
            types[i] = commandArgs[i].getClass();

            // Commands require primitives
            if (types[i] == Boolean.class) {
                types[i] = boolean.class;
            }
            if (types[i] == Integer.class) {
                types[i] = int.class;
            }
        }

        // Instantiating an object
        Constructor<T> constructor = clazz.getConstructor(types);
        T command = constructor.newInstance(commandArgs);

        // Some commands require StringEscape property to be set
        try {
            Method m = clazz.getMethod("setStringEscape", org.apache.camel.commands.StringEscape.class);
            m.invoke(command, stringEscape);
        } catch (Exception e) {
        }

        // Executing
        command.execute(camelController, ops, ops);
        return buffer.toString();
    }

    private String getCamelContextForRoute(String routeId) throws Exception {
        ArrayList<String> contextNames = new ArrayList<String>();

        for (CamelContext camelContext : camelController.getLocalCamelContexts()) {
            for (Route route : camelContext.getRoutes()) {
                if (routeId.equals(route.getId())) {
                    contextNames.add(camelContext.getName());
                    break;
                }
            }
        }

        if (contextNames.size() != 1) {
            StringBuffer error = new StringBuffer();
            error.append("Cannot infer CamelContext. Please provide manually.");

            if (contextNames.size() > 1) {
                error.append(" Contexts : " + contextNames.toString());
            }

            throw new org.crsh.cli.impl.SyntaxException(error.toString());
        }

        return contextNames.get(0);
    }

    private String getFirstCamelContextName() throws Exception {
        if (camelController.getLocalCamelContexts() == null || camelController.getLocalCamelContexts().size() == 0) {
            throw new org.crsh.cli.impl.SyntaxException("No CamelContexts available");
        }

        return camelController.getLocalCamelContexts().get(0).getName();
    }
}
