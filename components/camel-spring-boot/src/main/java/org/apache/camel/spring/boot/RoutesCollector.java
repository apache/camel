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
import java.util.EventObject;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.StartupListener;
import org.apache.camel.main.MainDurationEventNotifier;
import org.apache.camel.management.event.CamelContextStartedEvent;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.rest.RestsDefinition;
import org.apache.camel.spi.EventNotifier;
import org.apache.camel.support.EventNotifierSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.Ordered;
import org.springframework.core.io.Resource;
import org.springframework.util.AntPathMatcher;

/**
 * Collects routes and rests from the various sources (like Spring application context beans registry or opinionated
 * classpath locations) and injects these into the Camel context.
 */
public class RoutesCollector implements ApplicationListener<ContextRefreshedEvent>, Ordered {

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
        CamelContext camelContext = applicationContext.getBean(CamelContext.class);

        // only add and start Camel if its stopped (initial state)
        if (camelContext.getStatus().isStopped()) {
            LOG.debug("Post-processing CamelContext bean: {}", camelContext.getName());

            final AntPathMatcher matcher = new AntPathMatcher();
            for (RoutesBuilder routesBuilder : applicationContext.getBeansOfType(RoutesBuilder.class, configurationProperties.isIncludeNonSingletons(), true).values()) {
                // filter out abstract classes
                boolean abs = Modifier.isAbstract(routesBuilder.getClass().getModifiers());
                if (!abs) {
                    String name = routesBuilder.getClass().getName();
                    // make name as path so we can use ant path matcher
                    name = name.replace('.', '/');

                    String exclude = configurationProperties.getJavaRoutesExcludePattern();
                    String include = configurationProperties.getJavaRoutesIncludePattern();

                    boolean match = !"false".equals(include);
                    // exclude take precedence over include
                    if (match && ObjectHelper.isNotEmpty(exclude)) {
                        // there may be multiple separated by comma
                        String[] parts = exclude.split(",");
                        for (String part : parts) {
                            // must negate when excluding, and hence !
                            match = !matcher.match(part, name);
                            LOG.trace("Java RoutesBuilder: {} exclude filter: {} -> {}", name, part, match);
                            if (!match) {
                                break;
                            }
                        }
                    }
                    if (match && ObjectHelper.isNotEmpty(include)) {
                        // there may be multiple separated by comma
                        String[] parts = include.split(",");
                        for (String part : parts) {
                            match = matcher.match(part, name);
                            LOG.trace("Java RoutesBuilder: {} include filter: {} -> {}", name, part, match);
                            if (match) {
                                break;
                            }
                        }
                    }
                    LOG.debug("Java RoutesBuilder: {} accepted by include/exclude filter: {}", name, match);
                    if (match) {
                        try {
                            LOG.debug("Injecting following route into the CamelContext: {}", routesBuilder);
                            camelContext.addRoutes(routesBuilder);
                        } catch (Exception e) {
                            throw new CamelSpringBootInitializationException(e);
                        }
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

                    if (configurationProperties.getDurationMaxMessages() > 0 || configurationProperties.getDurationMaxIdleSeconds() > 0) {
                        if (configurationProperties.getDurationMaxMessages() > 0) {
                            LOG.info("CamelSpringBoot will terminate after processing {} messages", configurationProperties.getDurationMaxMessages());
                        }
                        if (configurationProperties.getDurationMaxIdleSeconds() > 0) {
                            LOG.info("CamelSpringBoot will terminate after being idle for more {} seconds", configurationProperties.getDurationMaxIdleSeconds());
                        }
                        // register lifecycle so we can trigger to shutdown the JVM when maximum number of messages has been processed
                        EventNotifier notifier = new MainDurationEventNotifier(camelContext,
                            configurationProperties.getDurationMaxMessages(), configurationProperties.getDurationMaxIdleSeconds(),
                            controller.getCompleted(), controller.getLatch(), true);
                        // register our event notifier
                        ServiceHelper.startService(notifier);
                        camelContext.getManagementStrategy().addEventNotifier(notifier);
                    }

                    if (configurationProperties.getDurationMaxSeconds() > 0) {
                        LOG.info("CamelSpringBoot will terminate after {} seconds", configurationProperties.getDurationMaxSeconds());
                        terminateMainControllerAfter(camelContext, configurationProperties.getDurationMaxSeconds(),
                            controller.getCompleted(), controller.getLatch());
                    }

                    camelContext.addStartupListener(new StartupListener() {
                        @Override
                        public void onCamelContextStarted(CamelContext context, boolean alreadyStarted) throws Exception {
                            // run the CamelMainRunController after the context has been started
                            // this way we ensure that NO_START flag is honoured as it's set as
                            // a thread local variable of the thread CamelMainRunController is
                            // not running on
                            if (!alreadyStarted) {
                                LOG.info("Starting CamelMainRunController to ensure the main thread keeps running");
                                controller.start();
                            }
                        }
                    });
                } else {
                    if (applicationContext instanceof ConfigurableApplicationContext) {
                        ConfigurableApplicationContext cac = (ConfigurableApplicationContext) applicationContext;

                        if (configurationProperties.getDurationMaxSeconds() > 0) {
                            LOG.info("CamelSpringBoot will terminate after {} seconds", configurationProperties.getDurationMaxSeconds());
                            terminateApplicationContext(cac, camelContext, configurationProperties.getDurationMaxSeconds());
                        }

                        if (configurationProperties.getDurationMaxMessages() > 0 || configurationProperties.getDurationMaxIdleSeconds() > 0) {

                            if (configurationProperties.getDurationMaxMessages() > 0) {
                                LOG.info("CamelSpringBoot will terminate after processing {} messages", configurationProperties.getDurationMaxMessages());
                            }
                            if (configurationProperties.getDurationMaxIdleSeconds() > 0) {
                                LOG.info("CamelSpringBoot will terminate after being idle for more {} seconds", configurationProperties.getDurationMaxIdleSeconds());
                            }
                            // needed by MainDurationEventNotifier to signal when we have processed the max messages
                            final AtomicBoolean completed = new AtomicBoolean();
                            final CountDownLatch latch = new CountDownLatch(1);

                            // register lifecycle so we can trigger to shutdown the JVM when maximum number of messages has been processed
                            EventNotifier notifier = new MainDurationEventNotifier(camelContext,
                                configurationProperties.getDurationMaxMessages(), configurationProperties.getDurationMaxIdleSeconds(),
                                completed, latch, false);
                            // register our event notifier
                            ServiceHelper.startService(notifier);
                            camelContext.getManagementStrategy().addEventNotifier(notifier);

                            terminateApplicationContext(cac, camelContext, latch);
                        }
                    }
                }

                if (!camelContextConfigurations.isEmpty()) {
                    // we want to call these notifications just after CamelContext has been fully started
                    // so use an event notifier to trigger when this happens
                    camelContext.getManagementStrategy().addEventNotifier(new EventNotifierSupport() {
                        @Override
                        public void notify(EventObject eventObject) throws Exception {
                            for (CamelContextConfiguration camelContextConfiguration : camelContextConfigurations) {
                                LOG.debug("CamelContextConfiguration found. Invoking afterApplicationStart: {}", camelContextConfiguration);
                                try {
                                    camelContextConfiguration.afterApplicationStart(camelContext);
                                } catch (Exception e) {
                                    LOG.warn("Error during calling afterApplicationStart due " + e.getMessage() + ". This exception is ignored", e);
                                }
                            }
                        }

                        @Override
                        public boolean isEnabled(EventObject eventObject) {
                            return eventObject instanceof CamelContextStartedEvent;
                        }
                    });
                }
            } catch (Exception e) {
                throw new CamelSpringBootInitializationException(e);
            }
        } else {
            LOG.debug("Camel already started, not adding routes.");
        }
    }

    @Override
    public int getOrder() {
        // RoutesCollector implements Ordered so that it's the
        // first Camel ApplicationListener to receive events,
        // SpringCamelContext should be the last one,
        // CamelContextFactoryBean should be second to last and then
        // RoutesCollector. This is important for startup as we want
        // all resources to be ready and all routes added to the 
        // context before we start CamelContext.
        // So the order should be:
        // 1. RoutesCollector (LOWEST_PRECEDENCE - 2)
        // 2. CamelContextFactoryBean (LOWEST_PRECEDENCE -1)
        // 3. SpringCamelContext (LOWEST_PRECEDENCE)
        return LOWEST_PRECEDENCE - 2;
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
        ScheduledExecutorService executorService = camelContext.getExecutorServiceManager().newSingleThreadScheduledExecutor(this, "CamelSpringBootTerminateTask");
        Runnable task = () -> {
            LOG.info("CamelSpringBoot triggering shutdown of the JVM.");
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

    private void terminateApplicationContext(final ConfigurableApplicationContext applicationContext, final CamelContext camelContext, int seconds) {
        ScheduledExecutorService executorService = camelContext.getExecutorServiceManager().newSingleThreadScheduledExecutor(this, "CamelSpringBootTerminateTask");
        Runnable task = () -> {
            LOG.info("CamelSpringBoot triggering shutdown of the JVM.");
            // we need to run a daemon thread to stop ourselves so this thread pool can be stopped nice also
            new Thread(applicationContext::close).start();
        };
        executorService.schedule(task, seconds, TimeUnit.SECONDS);
    }

    private void terminateApplicationContext(final ConfigurableApplicationContext applicationContext, final CamelContext camelContext, final CountDownLatch latch) {
        ExecutorService executorService = camelContext.getExecutorServiceManager().newSingleThreadExecutor(this, "CamelSpringBootTerminateTask");
        Runnable task = () -> {
            try {
                latch.await();
                LOG.info("CamelSpringBoot triggering shutdown of the JVM.");
                // we need to run a daemon thread to stop ourselves so this thread pool can be stopped nice also
                new Thread(applicationContext::close).start();
            } catch (Throwable e) {
                // ignore
            }
        };
        executorService.submit(task);
    }

}
