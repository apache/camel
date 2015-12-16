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
package org.apache.camel.spring.boot;

import java.io.FileNotFoundException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.model.RoutesDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.io.Resource;

/**
 * Collects routes from the various sources (like Spring application context beans registry or opinionated classpath
 * locations) and injects these into the Camel context.
 */
public class RoutesCollector implements ApplicationListener<ContextRefreshedEvent> {

    // Static collaborators

    private static final Logger LOG = LoggerFactory.getLogger(RoutesCollector.class);

    // Collaborators
    
    private final ApplicationContext applicationContext;

    private final List<CamelContextConfiguration> camelContextConfigurations;

    // Constructors

    public RoutesCollector(ApplicationContext applicationContext, List<CamelContextConfiguration> camelContextConfigurations) {
        this.applicationContext = applicationContext;
        this.camelContextConfigurations = new ArrayList<CamelContextConfiguration>(camelContextConfigurations);
    }

    // Overridden

    @Override
    public void onApplicationEvent(ContextRefreshedEvent contextRefreshedEvent) {
        ApplicationContext applicationContext = contextRefreshedEvent.getApplicationContext();
        // only listen to context refreshs of "my" applicationContext
        if (this.applicationContext.equals(applicationContext)) {
            CamelContext camelContext = contextRefreshedEvent.getApplicationContext().getBean(CamelContext.class);

            // only add and start Camel if its stopped (initial state)
            if (camelContext.getStatus().isStopped()) {
                LOG.debug("Post-processing CamelContext bean: {}", camelContext.getName());
                for (RoutesBuilder routesBuilder : applicationContext.getBeansOfType(RoutesBuilder.class).values()) {
                    // filter out abstract classes
                    boolean abs = Modifier.isAbstract(routesBuilder.getClass().getModifiers());
                    // filter out FatJarRouter which can be in the spring app context
                    boolean farJarRouter = FatJarRouter.class.equals(routesBuilder.getClass());
                    if (!abs && !farJarRouter) {
                        try {
                            LOG.debug("Injecting following route into the CamelContext: {}", routesBuilder);
                            camelContext.addRoutes(routesBuilder);
                        } catch (Exception e) {
                            throw new CamelSpringBootInitializationException(e);
                        }
                    }
                }

                try {
                    loadXmlRoutes(applicationContext, camelContext);

                    for (CamelContextConfiguration camelContextConfiguration : camelContextConfigurations) {
                        LOG.debug("CamelContextConfiguration found. Invoking: {}", camelContextConfiguration);
                        camelContextConfiguration.beforeApplicationStart(camelContext);
                    }

                    camelContext.start();
                } catch (Exception e) {
                    throw new CamelSpringBootInitializationException(e);
                }
            } else {
                LOG.debug("Camel already started, not adding routes.");
            }
        } else {
            LOG.debug("Ignore ContextRefreshedEvent: {}", contextRefreshedEvent);
        }
    }

    // Helpers

    private void loadXmlRoutes(ApplicationContext applicationContext, CamelContext camelContext) throws Exception {
        LOG.debug("Started XML routes detection. Scanning classpath (/camel/*.xml)...");
        try {
            Resource[] xmlRoutes = applicationContext.getResources("classpath:camel/*.xml");
            for (Resource xmlRoute : xmlRoutes) {
                RoutesDefinition xmlDefinition = camelContext.loadRoutesDefinition(xmlRoute.getInputStream());
                camelContext.addRouteDefinitions(xmlDefinition.getRoutes());
            }
        } catch (FileNotFoundException e) {
            LOG.debug("No XMl routes found in the classpath (/camel/*.xml). Skipping XML routes detection.");
        }
    }

}