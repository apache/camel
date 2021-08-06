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
package org.apache.camel.main;

import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.spi.BootstrapCloseable;

public class MainBootstrapCloseable implements BootstrapCloseable {

    private final MainSupport main;

    public MainBootstrapCloseable(MainSupport main) {
        this.main = main;
    }

    @Override
    public void close() {
        // in lightweight mode then clear up memory after bootstrap
        boolean lightweight = true;
        if (main.getCamelContext() != null) {
            lightweight = main.getCamelContext().adapt(ExtendedCamelContext.class).isLightweight();
        }

        if (lightweight) {
            if (main.initialProperties != null) {
                main.initialProperties.clear();
                main.initialProperties = null;
            }
            if (main.overrideProperties != null) {
                main.overrideProperties.clear();
                main.overrideProperties = null;
            }
            main.wildcardProperties.clear();
            main.wildcardProperties = null;

            // no longer in use
            main.mainConfigurationProperties.close();
            main.mainConfigurationProperties = null;
            main.routesCollector = null;
        }
    }
}
