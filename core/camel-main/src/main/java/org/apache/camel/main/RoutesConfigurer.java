/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.main;

import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.model.Model;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.model.rest.RestsDefinition;
import org.apache.camel.support.OrderedComparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * To configure routes using {@link RoutesCollector} which collects the routes from various sources.
 */
public class RoutesConfigurer {

    private static final Logger LOG = LoggerFactory.getLogger(RoutesConfigurer.class);

    private final RoutesCollector routesCollector;

    public RoutesConfigurer(RoutesCollector routesCollector) {
        this.routesCollector = routesCollector;
    }

    /**
     * Collects routes and rests from the various sources (like registry or opinionated
     * classpath locations) and injects these into the Camel context.
     *
     * @param camelContext  the Camel Context
     * @param config        the configuration
     */
    public void configureRoutes(CamelContext camelContext, DefaultConfigurationProperties config) {
        if (config.isRoutesCollectorEnabled()) {
            try {
                LOG.debug("RoutesCollectorEnabled: {}", routesCollector);
                final List<RoutesBuilder> routes = routesCollector.collectRoutesFromRegistry(camelContext,
                        config.getJavaRoutesExcludePattern(),
                        config.getJavaRoutesIncludePattern());

                // sort routes according to ordered
                routes.sort(OrderedComparator.get());
                // then add the routes
                for (RoutesBuilder routesBuilder : routes) {
                    LOG.debug("Injecting following route into the CamelContext: {}", routesBuilder);
                    camelContext.addRoutes(routesBuilder);
                }

                boolean scan = !config.getXmlRoutes().equals("false");
                if (scan) {
                    List<RoutesDefinition> defs = routesCollector.collectXmlRoutesFromDirectory(camelContext, config.getXmlRoutes());
                    for (RoutesDefinition def : defs) {
                        camelContext.getExtension(Model.class).addRouteDefinitions(def.getRoutes());
                    }
                }

                boolean scanRests = !config.getXmlRests().equals("false");
                if (scanRests) {
                    List<RestsDefinition> defs = routesCollector.collectXmlRestsFromDirectory(camelContext, config.getXmlRests());
                    for (RestsDefinition def : defs) {
                        camelContext.getExtension(Model.class).addRestDefinitions(def.getRests(), true);
                    }
                }
            } catch (Exception e) {
                throw RuntimeCamelException.wrapRuntimeException(e);
            }
        }
    }
}
