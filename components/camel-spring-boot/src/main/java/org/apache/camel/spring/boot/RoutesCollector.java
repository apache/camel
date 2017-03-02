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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.main.MainDurationEventNotifier;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.rest.RestsDefinition;
import org.apache.camel.spi.EventNotifier;
import org.apache.camel.util.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.io.Resource;

/**
 * Collects routes and rests from the various sources (like Spring application context beans registry or opinionated
 * classpath locations) and injects these into the Camel context.
 */
public class RoutesCollector implements ApplicationListener<ContextRefreshedEvent> {

    // Static collaborators

    private static final Logger LOG = LoggerFactory.getLogger(RoutesCollector.class);

    // Collaborators
    
    private final ApplicationContext applicationContext;

    private final List<CamelContextConfiguration> camelContextConfigurations;

    private final CamelConfigurationProperties configurationProperties;

    // Constructors

    public RoutesCollector(ApplicationContext applicationContext, List<CamelContextConfiguration> camelContextConfigurations,
                           CamelConfigurationProperties configurationProperties) {
        this.applicationContext = applicationContext;
        this.camelContextConfigurations = new ArrayList<CamelContextConfiguration>(camelContextConfigurations);
        this.configurationProperties = configurationProperties;
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
                for (RoutesBuilder routesBuilder : applicationContext.getBeansOfType(RoutesBuilder.class, configurationProperties.isIncludeNonSingletons(), true).values()) {
                    // filter out abstract classes
                    boolean abs = Modifier.isAbstract(routesBuilder.getClass().getModifiers());
                    if (!abs) {
                        try {
                            LOG.debug("Injecting following route into the CamelContext: {}", routesBuilder);
                            camelContext.addRoutes(routesBuilder);
                        } catch (Exception e) {
                            throw new CamelSpringBootInitializationException(e);
                        }
                    }
                }

                try {
                    boolean scan = !configurationProperties.getXmlRoutes().equals("false");
                    if (scan) {
                        loadXmlRoutes(applicationContext, camelContext, configurationProperties.getXmlRoutes());
                    }

                    boolean scanRests = !configurationProperties.getXmlRests().equals("false");
                    if (scanRests) {
                        loadXmlRests(applicationContext, camelContext, configurationProperties.getXmlRests());
                    }

                    for (CamelContextConfiguration camelContextConfiguration : camelContextConfigurations) {
                        LOG.debug("CamelContextConfiguration found. Invoking beforeApplicationStart: {}", camelContextConfiguration);
                        camelContextConfiguration.beforeApplicationStart(camelContext);
                    }

                    if (configurationProperties.isMainRunController()) {
                        CamelMainRunController controller = new CamelMainRunController(applicationContext, camelContext);

                        if (configurationProperties.getMainRunControllerMaxDurationMessages() > 0) {
                            LOG.info("CamelMainRunController will terminate after processing maximum {} messages", configurationProperties.getMainRunControllerMaxDurationMessages());
                            // register lifecycle so we can trigger to shutdown the JVM when maximum number of messages has been processed
                            EventNotifier notifier = new MainDurationEventNotifier(camelContext, configurationProperties.getMainRunControllerMaxDurationMessages(),
                                controller.getCompleted(), controller.getLatch());
                            // register our event notifier
                            ServiceHelper.startService(notifier);
                            camelContext.getManagementStrategy().addEventNotifier(notifier);
                        }

                        if (configurationProperties.getMainRunControllerMaxDurationSeconds() > 0) {
                            LOG.info("CamelMainRunController will terminate after {} seconds", configurationProperties.getMainRunControllerMaxDurationSeconds());
                            terminateMainControllerAfter(camelContext, configurationProperties.getMainRunControllerMaxDurationSeconds(),
                                controller.getCompleted(), controller.getLatch());
                        }

                        // controller will start Camel
                        LOG.info("Starting CamelMainRunController to ensure the main thread keeps running");
                        controller.start();
                    } else {
                        // start camel manually
                        maybeStart(camelContext);
                    }

                    for (CamelContextConfiguration camelContextConfiguration : camelContextConfigurations) {
                        LOG.debug("CamelContextConfiguration found. Invoking afterApplicationStart: {}", camelContextConfiguration);
                        camelContextConfiguration.afterApplicationStart(camelContext);
                    }
                } catch (Exception e) {
                    throw new CamelSpringBootInitializationException(e);
                }
            } else {
                LOG.debug("Camel already started, not adding routes.");
            }
        } else {
            LOG.debug("Ignore ContextRefreshedEvent: {}", event);
        }
    }

    private void maybeStart(CamelContext camelContext) throws Exception {
        // for example from unit testing we want to start Camel later and not when Spring framework
        // publish a ContextRefreshedEvent
        boolean skip = "true".equalsIgnoreCase(System.getProperty("skipStartingCamelContext"));
        if (skip) {
            LOG.info("Skipping starting CamelContext as system property skipStartingCamelContext is set to be true.");
        } else {
            camelContext.start();
        }
    }

    // Helpers

    private void loadXmlRoutes(ApplicationContext applicationContext, CamelContext camelContext, String directory) throws Exception {
        LOG.info("Loading additional Camel XML routes from: {}", directory);
        try {
            Resource[] xmlRoutes = applicationContext.getResources(directory);
            for (Resource xmlRoute : xmlRoutes) {
                LOG.debug("Found XML route: {}", xmlRoute);
                RoutesDefinition xmlDefinition = camelContext.loadRoutesDefinition(xmlRoute.getInputStream());
                camelContext.addRouteDefinitions(xmlDefinition.getRoutes());
            }
        } catch (FileNotFoundException e) {
            LOG.debug("No XML routes found in {}. Skipping XML routes detection.", directory);
        }
    }

    private void loadXmlRests(ApplicationContext applicationContext, CamelContext camelContext, String directory) {
        LOG.info("Loading additional Camel XML rests from: {}", directory);
        try {
            final Resource[] xmlRests = applicationContext.getResources(directory);
            for (final Resource xmlRest : xmlRests) {
                final RestsDefinition xmlDefinitions = camelContext.loadRestsDefinition(xmlRest.getInputStream());
                camelContext.addRestDefinitions(xmlDefinitions.getRests());
                for (final RestDefinition xmlDefinition : xmlDefinitions.getRests()) {
                    final List<RouteDefinition> routeDefinitions = xmlDefinition.asRouteDefinition(camelContext);
                    camelContext.addRouteDefinitions(routeDefinitions);
                }
            }
        } catch (FileNotFoundException e) {
            LOG.debug("No XML rests found in {}. Skipping XML rests detection.", directory);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void terminateMainControllerAfter(final CamelContext camelContext, int seconds, final AtomicBoolean completed, final CountDownLatch latch) {
        ScheduledExecutorService executorService = camelContext.getExecutorServiceManager().newSingleThreadScheduledExecutor(this, "CamelMainRunControllerTerminateTaks");
        Runnable task = () -> {
            LOG.info("CamelMainRunController max seconds triggering shutdown of the JVM.");
            try {
                camelContext.stop();
            } catch (Throwable e) {
                LOG.warn("Error during stopping CamelContext", e);
            } finally {
                completed.set(true);
                latch.countDown();
            }
        };
        executorService.schedule(task, seconds, TimeUnit.SECONDS);
    }

}
