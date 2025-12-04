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

package org.apache.camel.spi;

import org.apache.camel.CamelContext;

/**
 * A plugin interface that allows third-party components to perform initialization tasks when a CamelContext is being
 * configured and started.
 * <p>
 * Implementations of this interface are automatically discovered and loaded via the Java ServiceLoader mechanism. To
 * register a plugin, create a service provider configuration file at
 * {@code META-INF/services/org.apache.camel.spi.ContextServicePlugin} containing the fully qualified class name of your
 * implementation.
 * <p>
 * Common use cases include:
 * <ul>
 * <li>Registering beans in the Camel registry</li>
 * <li>Adding event notifiers for monitoring</li>
 * <li>Configuring global interceptors</li>
 * <li>Setting up custom type converters</li>
 * </ul>
 *
 * <h3>Example Usage:</h3>
 *
 * <pre>
 * <code>
 * public class MyContextServicePlugin implements ContextServicePlugin {
 *     &#64;Override
 *     public void load(CamelContext camelContext) {
 *         // Register a bean in the registry
 *         camelContext.getRegistry().bind("myBean", new MyBean());
 *
 *         // Add an event notifier
 *         camelContext.getManagementStrategy().addEventNotifier(new MyEventNotifier());
 *     }
 * }
 * </code>
 * </pre>
 *
 * @see org.apache.camel.impl.engine.DefaultContextServiceLoaderPlugin
 */
public interface ContextServicePlugin {

    /**
     * Called during CamelContext initialization to allow the plugin to configure or customize the context.
     * <p>
     * This method is invoked after the CamelContext has been created but before routes are started. Implementations
     * should perform any necessary setup operations such as registering beans, adding event notifiers, or configuring
     * global settings.
     *
     * @param camelContext the CamelContext being initialized, never {@code null}
     */
    void load(CamelContext camelContext);

    /**
     * Called during CamelContext stop. Use it to free allocated resources.
     *
     * @param camelContext the CamelContext being uninitialized, never {@code null}
     */
    default void unload(CamelContext camelContext) {
        // NO-OP
    }
}
