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
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;

/**
 * Abstract command for working with a single {@link org.apache.camel.CamelContext}
 */
public abstract class AbstractContextCommand extends AbstractCamelCommand {

    String context;

    /**
     * @param context The name of the Camel context.
     */
    protected AbstractContextCommand(String context) {
        this.context = context;
    }

    @Override
    public Object execute(CamelController camelController, PrintStream out, PrintStream err) throws Exception {
        if (camelController instanceof LocalCamelController) {
            return executeLocal((LocalCamelController) camelController, out, err);
        } else {
            boolean found = false;
            List<Map<String, String>> contexts = camelController.getCamelContexts();
            for (Map<String, String> entry : contexts) {
                String name = entry.get("name");
                if (context.equals(name)) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                err.println("Camel context " + context + " not found.");
                return null;
            } else {
                return performContextCommand(camelController, context, out, err);
            }
        }
    }

    protected Object executeLocal(LocalCamelController camelController, PrintStream out, PrintStream err) throws Exception {
        CamelContext camelContext = camelController.getLocalCamelContext(context);
        if (camelContext == null) {
            err.println("Camel context " + context + " not found.");
            return null;
        }

        // Setting thread context classloader to the bundle classloader to enable legacy code that relies on it
        ClassLoader oldClassloader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(camelContext.getApplicationContextClassLoader());
        try {
            return performContextCommand(camelController, camelContext.getName(), out, err);
        } finally {
            Thread.currentThread().setContextClassLoader(oldClassloader);
        }
    }

    /**
     * Perform Context-specific command
     *
     * @param camelController the Camel controller
     * @param contextName     the Camel context name
     * @param out             the output printer stream
     * @param err             the error print stream
     * @return response from command, or <tt>null</tt> if nothing to return
     * @throws Exception is thrown if error executing command
     */
    protected abstract Object performContextCommand(CamelController camelController, String contextName, PrintStream out, PrintStream err) throws Exception;

}
