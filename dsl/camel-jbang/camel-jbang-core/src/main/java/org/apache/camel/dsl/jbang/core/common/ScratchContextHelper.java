/*
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
package org.apache.camel.dsl.jbang.core.common;

import org.apache.camel.CamelContext;
import org.apache.camel.cli.connector.DefaultCliConnectorFactory;
import org.apache.camel.spi.CliConnectorFactory;

/**
 * The CamelContexts the Camel CLI runs for itself, such as the scratch context that evaluates an expression for the AI
 * tools: they are not integrations, so they run without the CLI connector. With it, such a context writes the status
 * file of this process ({@code ~/.camel/{pid}-status.json}) while it runs, and the MCP server, the TUI or camel ask,
 * which run for a long time, showed up as a running integration (CAMEL-26396).
 *
 * @since 4.24
 */
public final class ScratchContextHelper {

    private ScratchContextHelper() {
    }

    /**
     * The context, set up to start without the CLI connector, whether it is built already or not.
     */
    public static <T extends CamelContext> T withoutCliConnector(T context) {
        CliConnectorFactory disabled = disabledCliConnectorFactory();
        // a context that is not built yet takes the factory from its registry as it builds
        context.getRegistry().bind(CliConnectorFactory.FACTORY, CliConnectorFactory.class, disabled);
        // a built one has its factory already
        context.getCamelContextExtension().addContextPlugin(CliConnectorFactory.class, disabled);
        return context;
    }

    /**
     * A CLI connector factory that is turned off, to bind in the registry of a Camel Main the CLI runs for itself.
     */
    public static CliConnectorFactory disabledCliConnectorFactory() {
        CliConnectorFactory factory = new DefaultCliConnectorFactory();
        factory.setEnabled(false);
        return factory;
    }
}
