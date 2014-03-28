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

import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The <code>Container</code> interface defines an object that can be used
 * to customize all Camel CONTEXTS created.
 * <p/>
 * A container can be used to globally intercept and customize Camel CONTEXTS,
 * by registering a <code>LifecycleStrategy</code>, a <code>ProcessorFactory</code>,
 * or any other SPI object.
 */
public interface Container {

    /**
     * The <code>Instance</code> class holds a <code>Container</code> singleton.
     */
    public static final class Instance {

        private static final Logger LOG = LoggerFactory.getLogger(Container.class);
        private static Container container;
        private static final Set<CamelContext> CONTEXTS = new LinkedHashSet<CamelContext>();

        private Instance() {
        }

        /**
         * Access the registered Container.
         *
         * @return the Container singleton
         */
        public static Container get() {
            return container;
        }

        /**
         * Register the Container.
         *
         * @param container the Container to register
         */
        public static void set(Container container) {
            Instance.container = container;

            if (container == null) {
                CONTEXTS.clear();
            } else if (!CONTEXTS.isEmpty()) {
                // manage any pending CamelContext which was started before a Container was set
                for (CamelContext context : CONTEXTS) {
                    manageCamelContext(container, context);
                }
                CONTEXTS.clear();
            }
        }

        /**
         * Called by Camel when a <code>CamelContext</code> has been created.
         *
         * @param camelContext the newly created CamelContext
         */
        public static void manage(CamelContext camelContext) {
            Container cnt = container;
            if (cnt != null) {
                manageCamelContext(cnt, camelContext);
            } else {
                // Container not yet set so need to remember this CamelContext
                CONTEXTS.add(camelContext);
            }
        }

        private static void manageCamelContext(Container container, CamelContext context) {
            try {
                container.manage(context);
            } catch (Throwable t) {
                LOG.warn("Error during manage CamelContext " + context.getName() + ". This exception is ignored.", t);
            }
        }

        /**
         * Called by Camel when a <code>CamelContext</code> has been destroyed.
         *
         * @param camelContext the CamelContext which has been destroyed
         */
        public static void unmanage(CamelContext camelContext) {
            CONTEXTS.remove(camelContext);
        }
    }

    /**
     * Called by Camel when a <code>CamelContext</code> has been created.
     *
     * @param camelContext the newly created CamelContext
     */
    void manage(CamelContext camelContext);

}
