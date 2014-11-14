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
package org.apache.camel.spi;

import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultCamelContextRegistry;

/**
 * A global registry for camel contexts.
 * <p/>
 * The runtime registers all contexts that derive from {@link DefaultCamelContext} automatically.
 */
public interface CamelContextRegistry {

    /**
     * The registry singleton
     */
    CamelContextRegistry INSTANCE = new DefaultCamelContextRegistry();

    /**
     * A listener that can be registered with he registry
     */
    public class Listener {

        /**
         * Called when a context is added to the registry
         */
        public void contextAdded(CamelContext camelContext) {
        }

        /**
         * Called when a context is removed from the registry
         */
        public void contextRemoved(CamelContext camelContext) {
        }
    }

    /**
     * Add the given listener to the registry
     *
     * @param withCallback If true, the given listener is called with the set of already registered contexts
     */
    void addListener(Listener listener, boolean withCallback);

    /**
     * Remove the given listener from the registry
     *
     * @param withCallback If true, the given listener is called with the set of already registered contexts
     */
    void removeListener(Listener listener, boolean withCallback);

    /**
     * Get the set of registered contexts
     */
    Set<CamelContext> getContexts();

    /**
     * Get the set of registered contexts for the given name.
     * <p/>
     * Because the camel context name property is neither unique nor immutable
     * the returned set may vary for the same name.
     */
    Set<CamelContext> getContexts(String name);

    /**
     * Get the registered context for the given name.
     *
     * @return The first context in the set
     * @throws IllegalStateException when there is no registered context for the given name
     * @see CamelContextRegistry#getContexts(String)
     */
    CamelContext getRequiredContext(String name);

    /**
     * Get the registered context for the given name.
     *
     * @return The first context in the set or null
     * @see CamelContextRegistry#getContexts(String)
     */
    CamelContext getContext(String name);

    /**
     * Needed for testing purposes.
     */
    void clear();

}