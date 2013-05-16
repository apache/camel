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

import org.apache.camel.CamelContext;

/**
 * The <code>Container</code> interface defines an object that can be used
 * to customize all Camel contexts created.
 *
 * A container can be used to globally intercept and customize Camel contexts,
 * by registering a <code>LifecycleStrategy</code>, a <code>ProcessorFactory</code>,
 * or any other SPI object.
 */
public interface Container {

    /**
     * The <code>Instance</code> class holds a <code>Container</code> singleton.
     */
    public static final class Instance {

        private Instance() {}

        private static Container container;

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
        }

        /**
         * Called by Camel when a <code>CamelContext</code> has been created.
         *
         * @param camelContext the newly created CamelContext
         */
        public static void manage(CamelContext camelContext) {
            Container cnt = container;
            if (cnt != null) {
                cnt.manage(camelContext);
            }
        }
    }

    /**
     * Called by Camel when a <code>CamelContext</code> has been created.
     *
     * @param camelContext the newly created CamelContext
     */
    void manage(CamelContext camelContext);

}
