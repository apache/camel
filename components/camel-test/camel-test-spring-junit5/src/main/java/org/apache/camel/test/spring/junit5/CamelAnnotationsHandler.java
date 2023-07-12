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
package org.apache.camel.test.spring.junit5;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.apache.camel.api.management.ManagedCamelContext;
import org.apache.camel.api.management.mbean.ManagedCamelContextMBean;
import org.apache.camel.component.mock.InterceptSendToMockEndpointStrategy;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.debugger.DefaultDebugger;
import org.apache.camel.spi.Breakpoint;
import org.apache.camel.spi.Debugger;
import org.apache.camel.spi.EventNotifier;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.spring.SpringCamelContext;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;

import static org.apache.camel.test.junit5.TestSupport.isCamelDebugPresent;

public final class CamelAnnotationsHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(CamelAnnotationsHandler.class);

    private CamelAnnotationsHandler() {
    }

    /**
     * Cleanup/restore global state to defaults / pre-test values after the test setup is complete.
     *
     */
    public static void cleanup() {
        DefaultCamelContext.clearOptions();
    }

    /**
     * Handles @ExcludeRoutes to make it easier to exclude other routes when testing with Spring.
     *
     * @param testClass the test class being executed
     */
    public static void handleExcludeRoutes(Class<?> testClass) {
        String key = SpringCamelContext.EXCLUDE_ROUTES;
        String exists = System.getProperty(key);
        if (exists != null) {
            LOGGER.warn("The JVM property {} is set, but not supported anymore.", key);
        }

        if (testClass.isAnnotationPresent(ExcludeRoutes.class)) {
            Class<?>[] routes = testClass.getAnnotation(ExcludeRoutes.class).value();
            // need to setup this as a JVM system property
            StringJoiner routesBuilder = new StringJoiner(",");
            for (Class<?> clazz : routes) {
                routesBuilder.add(clazz.getName());
            }
            String value = routesBuilder.toString();
            LOGGER.info("@ExcludeRoutes annotation found. Setting up JVM property {}={}", key, value);
            DefaultCamelContext.setExcludeRoutes(value);
        }
    }

    /**
     * Handles disabling of JMX on Camel contexts based on {@link DisableJmx}.
     *
     * @param testClass the test class being executed
     */
    public static void handleDisableJmx(Class<?> testClass) {
        if (isCamelDebugPresent()) {
            LOGGER.info("Enabling Camel JMX as camel-debug has been found in the classpath.");
            DefaultCamelContext.setDisableJmx(false);
        } else if (testClass.isAnnotationPresent(DisableJmx.class)) {
            if (testClass.getAnnotation(DisableJmx.class).value()) {
                LOGGER.info("Disabling Camel JMX globally as DisableJmx annotation was found and disableJmx is set to true.");
                DefaultCamelContext.setDisableJmx(true);
            } else {
                LOGGER.info("Enabling Camel JMX as DisableJmx annotation was found and disableJmx is set to false.");
                DefaultCamelContext.setDisableJmx(false);
            }
        } else if (!testClass.isAnnotationPresent(EnableRouteCoverage.class)) {
            // route coverage need JMX so do not disable it by default
            LOGGER.info(
                    "Disabling Camel JMX globally for tests by default. Use the DisableJMX annotation to override the default setting.");
            DefaultCamelContext.setDisableJmx(true);
        } else {
            LOGGER.info("Enabling Camel JMX as EnableRouteCoverage is used.");
            DefaultCamelContext.setDisableJmx(false);
        }
    }

    /**
     * Handles disabling of JMX on Camel contexts based on {@link EnableRouteCoverage}.
     *
     * @param context   the initialized Spring context
     * @param testClass the test class being executed
     */
    public static void handleRouteCoverage(ConfigurableApplicationContext context, Class<?> testClass, Function testMethod)
            throws Exception {
        if (testClass.isAnnotationPresent(EnableRouteCoverage.class)) {
            System.setProperty(CamelTestSupport.ROUTE_COVERAGE_ENABLED, "true");

            CamelSpringTestHelper.doToSpringCamelContexts(context, new CamelSpringTestHelper.DoToSpringCamelContextsStrategy() {

                @Override
                public void execute(String contextName, SpringCamelContext camelContext) throws Exception {
                    LOGGER.info("Enabling RouteCoverage");
                    EventNotifier notifier = new RouteCoverageEventNotifier(testClass.getName(), testMethod);
                    camelContext.addService(notifier, true);
                    camelContext.getManagementStrategy().addEventNotifier(notifier);
                }
            });
        }
    }

    public static void handleRouteCoverageDump(
            ConfigurableApplicationContext context, Class<?> testClass,
            Function<CamelSpringTestHelper.DoToSpringCamelContextsStrategy, String> testMethod)
            throws Exception {
        if (testClass.isAnnotationPresent(EnableRouteCoverage.class)) {
            CamelSpringTestHelper.doToSpringCamelContexts(context, new CamelSpringTestHelper.DoToSpringCamelContextsStrategy() {

                @Override
                public void execute(String contextName, SpringCamelContext camelContext) throws Exception {
                    LOGGER.debug("Dumping RouteCoverage");

                    String testMethodName = testMethod.apply(this);
                    RouteCoverageDumper.dumpRouteCoverage(camelContext, testClass.getName(), testMethodName);

                    // reset JMX statistics
                    ManagedCamelContext managedCamelContext
                            = camelContext.getCamelContextExtension().getContextPlugin(ManagedCamelContext.class);
                    if (managedCamelContext != null) {
                        ManagedCamelContextMBean mBean = managedCamelContext.getManagedCamelContext();
                        LOGGER.debug("Resetting JMX statistics for RouteCoverage");
                        mBean.reset(true);
                    }

                    // turn off dumping one more time by removing the event listener (which would dump as well when Camel is stopping)
                    // but this method was explicit invoked to dump such as from afterTest callbacks from JUnit.
                    RouteCoverageEventNotifier eventNotifier = camelContext.hasService(RouteCoverageEventNotifier.class);
                    if (eventNotifier != null) {
                        camelContext.getManagementStrategy().removeEventNotifier(eventNotifier);
                        camelContext.removeService(eventNotifier);
                    }
                }
            });
        }
    }

    public static void handleProvidesBreakpoint(ConfigurableApplicationContext context, Class<?> testClass) throws Exception {
        Collection<Method> methods = CamelSpringTestHelper.getAllMethods(testClass);
        final List<Breakpoint> breakpoints = new LinkedList<>();

        for (Method method : methods) {
            if (AnnotationUtils.findAnnotation(method, ProvidesBreakpoint.class) != null) {
                Class<?>[] argTypes = method.getParameterTypes();
                if (argTypes.length != 0) {
                    throw new IllegalArgumentException(
                            "Method [" + method.getName()
                                                       + "] is annotated with ProvidesBreakpoint but is not a no-argument method.");
                } else if (!Breakpoint.class.isAssignableFrom(method.getReturnType())) {
                    throw new IllegalArgumentException(
                            "Method [" + method.getName()
                                                       + "] is annotated with ProvidesBreakpoint but does not return a Breakpoint.");
                } else if (!Modifier.isStatic(method.getModifiers())) {
                    throw new IllegalArgumentException(
                            "Method [" + method.getName()
                                                       + "] is annotated with ProvidesBreakpoint but is not static.");
                } else if (!Modifier.isPublic(method.getModifiers())) {
                    throw new IllegalArgumentException(
                            "Method [" + method.getName()
                                                       + "] is annotated with ProvidesBreakpoint but is not public.");
                }

                try {
                    breakpoints.add((Breakpoint) method.invoke(null));
                } catch (Exception e) {
                    throw new RuntimeException(
                            "Method [" + method.getName()
                                               + "] threw exception during evaluation.",
                            e);
                }
            }
        }

        if (!breakpoints.isEmpty()) {
            CamelSpringTestHelper.doToSpringCamelContexts(context, new CamelSpringTestHelper.DoToSpringCamelContextsStrategy() {

                public void execute(String contextName, SpringCamelContext camelContext)
                        throws Exception {

                    // automatic turn on debugging when we have breakpoints
                    camelContext.setDebugging(true);

                    Debugger debugger = camelContext.getDebugger();
                    if (debugger == null) {
                        debugger = new DefaultDebugger();
                        camelContext.setDebugger(debugger);
                    }

                    for (Breakpoint breakpoint : breakpoints) {
                        LOGGER.info("Adding Breakpoint [{}] to CamelContext with name [{}].", breakpoint, contextName);
                        debugger.addBreakpoint(breakpoint);
                    }
                }
            });
        }
    }

    /**
     * Handles updating shutdown timeouts on Camel contexts based on {@link ShutdownTimeout}.
     *
     * @param context   the initialized Spring context
     * @param testClass the test class being executed
     */
    public static void handleShutdownTimeout(ConfigurableApplicationContext context, Class<?> testClass) throws Exception {
        final int shutdownTimeout;
        final TimeUnit shutdownTimeUnit;
        if (testClass.isAnnotationPresent(ShutdownTimeout.class)) {
            shutdownTimeout = testClass.getAnnotation(ShutdownTimeout.class).value();
            shutdownTimeUnit = testClass.getAnnotation(ShutdownTimeout.class).timeUnit();
        } else {
            shutdownTimeout = 10;
            shutdownTimeUnit = TimeUnit.SECONDS;
        }

        CamelSpringTestHelper.doToSpringCamelContexts(context, new CamelSpringTestHelper.DoToSpringCamelContextsStrategy() {

            public void execute(String contextName, SpringCamelContext camelContext)
                    throws Exception {
                LOGGER.info("Setting shutdown timeout to [{} {}] on CamelContext with name [{}].", shutdownTimeout,
                        shutdownTimeUnit, contextName);
                camelContext.getShutdownStrategy().setTimeout(shutdownTimeout);
                camelContext.getShutdownStrategy().setTimeUnit(shutdownTimeUnit);
            }
        });
    }

    /**
     * Handles auto-intercepting of endpoints with mocks based on {@link MockEndpoints}.
     *
     * @param context   the initialized Spring context
     * @param testClass the test class being executed
     */
    public static void handleMockEndpoints(ConfigurableApplicationContext context, Class<?> testClass) throws Exception {
        if (testClass.isAnnotationPresent(MockEndpoints.class)) {
            final String mockEndpoints = testClass.getAnnotation(MockEndpoints.class).value();
            CamelSpringTestHelper.doToSpringCamelContexts(context, new CamelSpringTestHelper.DoToSpringCamelContextsStrategy() {

                public void execute(String contextName, SpringCamelContext camelContext)
                        throws Exception {
                    LOGGER.info("Enabling auto mocking of endpoints matching pattern [{}] on CamelContext with name [{}].",
                            mockEndpoints, contextName);
                    camelContext.getCamelContextExtension()
                            .registerEndpointCallback(new InterceptSendToMockEndpointStrategy(mockEndpoints));
                }
            });
        }
    }

    /**
     * Handles auto-intercepting of endpoints with mocks based on {@link MockEndpointsAndSkip} and skipping the original
     * endpoint.
     *
     * @param context   the initialized Spring context
     * @param testClass the test class being executed
     */
    public static void handleMockEndpointsAndSkip(ConfigurableApplicationContext context, Class<?> testClass) throws Exception {
        if (testClass.isAnnotationPresent(MockEndpointsAndSkip.class)) {
            final String mockEndpoints = testClass.getAnnotation(MockEndpointsAndSkip.class).value();
            CamelSpringTestHelper.doToSpringCamelContexts(context, new CamelSpringTestHelper.DoToSpringCamelContextsStrategy() {

                public void execute(String contextName, SpringCamelContext camelContext)
                        throws Exception {
                    // resolve the property place holders of the mockEndpoints
                    String mockEndpointsValue = camelContext.resolvePropertyPlaceholders(mockEndpoints);
                    LOGGER.info(
                            "Enabling auto mocking and skipping of endpoints matching pattern [{}] on CamelContext with name [{}].",
                            mockEndpointsValue, contextName);
                    camelContext.getCamelContextExtension()
                            .registerEndpointCallback(new InterceptSendToMockEndpointStrategy(mockEndpointsValue, true));
                }
            });
        }
    }

    /**
     * Handles override this method to include and override properties with the Camel
     * {@link org.apache.camel.component.properties.PropertiesComponent}.
     *
     * @param context   the initialized Spring context
     * @param testClass the test class being executed
     */
    public static void handleUseOverridePropertiesWithPropertiesComponent(
            ConfigurableApplicationContext context, Class<?> testClass)
            throws Exception {
        Collection<Method> methods = CamelSpringTestHelper.getAllMethods(testClass);
        final List<Properties> properties = new LinkedList<>();

        for (Method method : methods) {
            if (AnnotationUtils.findAnnotation(method, UseOverridePropertiesWithPropertiesComponent.class) != null) {
                Class<?>[] argTypes = method.getParameterTypes();
                if (argTypes.length > 0) {
                    throw new IllegalArgumentException(
                            "Method [" + method.getName()
                                                       + "] is annotated with UseOverridePropertiesWithPropertiesComponent but is not a no-argument method.");
                } else if (!Properties.class.isAssignableFrom(method.getReturnType())) {
                    throw new IllegalArgumentException(
                            "Method [" + method.getName()
                                                       + "] is annotated with UseOverridePropertiesWithPropertiesComponent but does not return a java.util.Properties.");
                } else if (!Modifier.isStatic(method.getModifiers())) {
                    throw new IllegalArgumentException(
                            "Method [" + method.getName()
                                                       + "] is annotated with UseOverridePropertiesWithPropertiesComponent but is not static.");
                } else if (!Modifier.isPublic(method.getModifiers())) {
                    throw new IllegalArgumentException(
                            "Method [" + method.getName()
                                                       + "] is annotated with UseOverridePropertiesWithPropertiesComponent but is not public.");
                }

                try {
                    properties.add((Properties) method.invoke(null));
                } catch (Exception e) {
                    throw new RuntimeException(
                            "Method [" + method.getName()
                                               + "] threw exception during evaluation.",
                            e);
                }
            }
        }

        Properties extra = new Properties();
        for (Properties prop : properties) {
            extra.putAll(prop);
        }

        if (!extra.isEmpty()) {
            context.addBeanFactoryPostProcessor(beanFactory -> beanFactory.addBeanPostProcessor(new BeanPostProcessor() {
                @Override
                public Object postProcessBeforeInitialization(Object bean, String beanName) {
                    if (bean instanceof PropertiesComponent pc) {
                        LOGGER.info("Using {} properties to override any existing properties on the PropertiesComponent",
                                extra.size());
                        pc.setOverrideProperties(extra);
                    }
                    return bean;
                }
            }));
        }
    }

    /**
     * Handles starting of Camel contexts based on {@link UseAdviceWith} and other state in the JVM.
     *
     * @param context   the initialized Spring context
     * @param testClass the test class being executed
     */
    public static void handleCamelContextStartup(ConfigurableApplicationContext context, Class<?> testClass) throws Exception {
        boolean skip = "true".equalsIgnoreCase(System.getProperty("skipStartingCamelContext"));
        if (skip) {
            LOGGER.info("Skipping starting CamelContext(s) as system property skipStartingCamelContext is set to be true.");
        } else if (testClass.isAnnotationPresent(UseAdviceWith.class)) {
            if (testClass.getAnnotation(UseAdviceWith.class).value()) {
                LOGGER.info(
                        "Skipping starting CamelContext(s) as UseAdviceWith annotation was found and isUseAdviceWith is set to true.");
                skip = true;
            } else {
                LOGGER.info(
                        "Starting CamelContext(s) as UseAdviceWith annotation was found, but isUseAdviceWith is set to false.");
                skip = false;
            }
        }

        if (!skip) {
            CamelSpringTestHelper.doToSpringCamelContexts(context, (contextName, camelContext) -> {
                if (!camelContext.isStarted()) {
                    LOGGER.info("Starting CamelContext with name [{}].", contextName);
                    camelContext.start();
                } else {
                    LOGGER.debug("CamelContext with name [{}] already started.", contextName);
                }
            });
        }
    }

}
