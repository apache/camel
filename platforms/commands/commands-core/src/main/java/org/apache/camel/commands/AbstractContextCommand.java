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

import org.apache.camel.CamelContext;
import org.apache.camel.spi.Required;

/**
 * Abstract command for working with a single {@link org.apache.camel.CamelContext}
 */
public abstract class AbstractContextCommand extends AbstractCamelCommand {

    /**
     * The name of the Camel context.
     */
    @Required
    public abstract String getContext();

    @Override
    public Object execute(CamelController camelController, PrintStream out, PrintStream err) throws Exception {
        CamelContext camelContext = camelController.getCamelContext(getContext());
        if (camelContext == null) {
            err.println("Camel context " + getContext() + " not found.");
            return null;
        }

        // Setting thread context classloader to the bundle classloader to enable legacy code that relies on it
        ClassLoader oldClassloader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(camelContext.getApplicationContextClassLoader());
        try {
            performContextCommand(camelContext, out, err);
        } finally {
            Thread.currentThread().setContextClassLoader(oldClassloader);
        }
        return null;
    }

    /**
     * Perform Context-specific command
     *
     * @param camelContext non-null {@link CamelContext}
     * @param out          the output printer stream
     * @param err          the error print stream
     */
    protected abstract void performContextCommand(CamelContext camelContext, PrintStream out, PrintStream err) throws Exception;

}
