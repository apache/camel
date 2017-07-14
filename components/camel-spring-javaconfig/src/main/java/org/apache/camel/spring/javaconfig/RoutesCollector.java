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
package org.apache.camel.spring.javaconfig;

import java.lang.reflect.Modifier;

import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.Ordered;

/**
 * Collects routes and rests from the various sources (like Spring application context beans registry or opinionated
 * classpath locations) and injects these into the Camel context.
 */
public class RoutesCollector implements ApplicationListener<ContextRefreshedEvent>, Ordered {

    // Static collaborators

    private static final Logger LOG = LoggerFactory.getLogger(RoutesCollector.class);

    // Collaborators

    private final ApplicationContext applicationContext;
    private final CamelConfiguration configuration;

    // Constructors

    public RoutesCollector(ApplicationContext applicationContext, CamelConfiguration configuration) {
        this.applicationContext = applicationContext;
        this.configuration = configuration;
    }

    // Overridden

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        ApplicationContext applicationContext = event.getApplicationContext();

        // only listen to context refresh of "my" applicationContext
        if (this.applicationContext.equals(applicationContext)) {

            CamelContext camelContext = event.getApplicationContext().getBean(CamelContext.class);

            // only add and start Camel if its stopped (initial state)
            if (camelContext.getStatus().isStopped()) {
                LOG.debug("Post-processing CamelContext bean: {}", camelContext.getName());
                for (RoutesBuilder routesBuilder : configuration.routes()) {
                    // filter out abstract classes
                    boolean abs = Modifier.isAbstract(routesBuilder.getClass().getModifiers());
                    if (!abs) {
                        try {
                            LOG.debug("Injecting following route into the CamelContext: {}", routesBuilder);
                            camelContext.addRoutes(routesBuilder);
                        } catch (Exception e) {
                            throw new CamelSpringJavaconfigInitializationException(e);
                        }
                    }
                }

                try {
                    boolean skip = "true".equalsIgnoreCase(System.getProperty("skipStartingCamelContext"));
                    if (skip) {
                        LOG.info("Skipping starting CamelContext(s) as system property skipStartingCamelContext is set to be true.");
                    } else {
                        // start camel
                        camelContext.start();
                    }

                } catch (Exception e) {
                    throw new CamelSpringJavaconfigInitializationException(e);
                }

            }
        } else {
            LOG.debug("Ignore ContextRefreshedEvent: {}", event);
        }
    }

    @Override
    public int getOrder() {
        // we want the RoutesCollector to receive ContextRefreshedEvent
        // before SpringCamelContext (see SpringCamelContext::getOrder)
        return LOWEST_PRECEDENCE - 1;
    }

}
