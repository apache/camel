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
package org.apache.camel.test.spring;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.camel.component.properties.PropertiesComponent;
import org.apache.camel.impl.DefaultDebugger;
import org.apache.camel.impl.InterceptSendToMockEndpointStrategy;
import org.apache.camel.management.JmxSystemPropertyKeys;
import org.apache.camel.spi.Breakpoint;
import org.apache.camel.spi.Debugger;
import org.apache.camel.spring.SpringCamelContext;
import org.apache.camel.test.spring.CamelSpringTestHelper.DoToSpringCamelContextsStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.test.context.MergedContextConfiguration;
import org.springframework.test.context.support.DelegatingSmartContextLoader;

import static org.apache.camel.test.spring.CamelSpringTestHelper.getAllMethods;

/**
 * CamelSpringDelegatingTestContextLoader which fixes issues in Camel's JavaConfigContextLoader. (adds support for Camel's test annotations)
 * <br>
 * <em>This loader can handle either classes or locations for configuring the context.</em>
 * <br>
 * NOTE: This TestContextLoader doesn't support the annotation of ExcludeRoutes now.
 */
public class CamelSpringDelegatingTestContextLoader extends DelegatingSmartContextLoader {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public ApplicationContext loadContext(MergedContextConfiguration mergedConfig) throws Exception {
        
        Class<?> testClass = getTestClass();
        
        if (logger.isDebugEnabled()) {
            logger.debug("Loading ApplicationContext for merged context configuration [{}].", mergedConfig);
        }
        
        // Pre CamelContext(s) instantiation setup
        handleDisableJmx(null, testClass);
        
        try {
            SpringCamelContext.setNoStart(true);
            System.setProperty("skipStartingCamelContext", "true");
            ConfigurableApplicationContext context = (ConfigurableApplicationContext) super.loadContext(mergedConfig);
            SpringCamelContext.setNoStart(false);
            System.clearProperty("skipStartingCamelContext");
            return loadContext(context, testClass);
        } finally {
            cleanup(testClass);
        }
    }

    /**
     * Performs the bulk of the Spring application context loading/customization.
     *
     * @param context the partially configured context.  The context should have the bean definitions loaded, but nothing else.
     * @param testClass the test class being executed
     * @return the initialized (refreshed) Spring application context
     *
     * @throws Exception if there is an error during initialization/customization
     */
    public ApplicationContext loadContext(ConfigurableApplicationContext context, Class<?> testClass)
        throws Exception {
            
        AnnotationConfigUtils.registerAnnotationConfigProcessors((BeanDefinitionRegistry) context);

        // Post CamelContext(s) instantiation but pre CamelContext(s) start setup
        handleProvidesBreakpoint(context, testClass);
        handleShutdownTimeout(context, testClass);
        handleMockEndpoints(context, testClass);
        handleMockEndpointsAndSkip(context, testClass);
        handleUseOverridePropertiesWithPropertiesComponent(context, testClass);
        
        // CamelContext(s) startup
        handleCamelContextStartup(context, testClass);
        
        return context;
    }
    
    /**
     * Cleanup/restore global state to defaults / pre-test values after the test setup
     * is complete. 
     * 
     * @param testClass the test class being executed
     */
    protected void cleanup(Class<?> testClass) {
        SpringCamelContext.setNoStart(false);
        
        if (testClass.isAnnotationPresent(DisableJmx.class)) {
            if (CamelSpringTestHelper.getOriginalJmxDisabled() == null) {
                System.clearProperty(JmxSystemPropertyKeys.DISABLED);
            } else {
                System.setProperty(JmxSystemPropertyKeys.DISABLED,
                    CamelSpringTestHelper.getOriginalJmxDisabled());
            }
        }
    }
    
    /**
     * Handles disabling of JMX on Camel contexts based on {@link DisableJmx}.
     *
     * @param context the initialized Spring context
     * @param testClass the test class being executed
     */
    protected void handleDisableJmx(ConfigurableApplicationContext context, Class<?> testClass) {
        CamelSpringTestHelper.setOriginalJmxDisabledValue(System.getProperty(JmxSystemPropertyKeys.DISABLED));
        
        if (testClass.isAnnotationPresent(DisableJmx.class)) {
            if (testClass.getAnnotation(DisableJmx.class).value()) {
                logger.info("Disabling Camel JMX globally as DisableJmx annotation was found and disableJmx is set to true.");
                System.setProperty(JmxSystemPropertyKeys.DISABLED, "true");
                
            } else {
                logger.info("Enabling Camel JMX as DisableJmx annotation was found and disableJmx is set to false.");
                System.clearProperty(JmxSystemPropertyKeys.DISABLED);
            }
        } else {
            logger.info("Disabling Camel JMX globally for tests by default. Use the DisableJMX annotation to override the default setting.");
            System.setProperty(JmxSystemPropertyKeys.DISABLED, "true");
        }
    }
    
    /**
     * Handles the processing of the {@link ProvidesBreakpoint} annotation on a test class.  Exists here
     * as it is needed in 
     *
     * @param context the initialized Spring context containing the Camel context(s) to insert breakpoints into 
     * @param testClass the test class being processed
     *
     * @throws Exception if there is an error processing the class
     */
    protected void handleProvidesBreakpoint(ConfigurableApplicationContext context, Class<?> testClass) throws Exception {
        Collection<Method> methods = getAllMethods(testClass);
        final List<Breakpoint> breakpoints = new LinkedList<Breakpoint>();
        
        for (Method method : methods) {
            if (AnnotationUtils.findAnnotation(method, ProvidesBreakpoint.class) != null) {
                Class<?>[] argTypes = method.getParameterTypes();
                if (argTypes.length != 0) {
                    throw new IllegalArgumentException("Method [" + method.getName()
                           + "] is annotated with ProvidesBreakpoint but is not a no-argument method.");
                } else if (!Breakpoint.class.isAssignableFrom(method.getReturnType())) {
                    throw new IllegalArgumentException("Method [" + method.getName()
                           + "] is annotated with ProvidesBreakpoint but does not return a Breakpoint.");
                } else if (!Modifier.isStatic(method.getModifiers())) {
                    throw new IllegalArgumentException("Method [" + method.getName()
                           + "] is annotated with ProvidesBreakpoint but is not static.");
                } else if (!Modifier.isPublic(method.getModifiers())) {
                    throw new IllegalArgumentException("Method [" + method.getName()
                           + "] is annotated with ProvidesBreakpoint but is not public.");
                }
                
                try {
                    breakpoints.add((Breakpoint) method.invoke(null));
                } catch (Exception e) {
                    throw new RuntimeException("Method [" + method.getName()
                           + "] threw exception during evaluation.", e);
                }
            }
        }
        
        if (breakpoints.size() != 0) {
            CamelSpringTestHelper.doToSpringCamelContexts(context, new DoToSpringCamelContextsStrategy() {
                
                public void execute(String contextName, SpringCamelContext camelContext)
                    throws Exception {
                    Debugger debugger = camelContext.getDebugger();
                    if (debugger == null) {
                        debugger = new DefaultDebugger();
                        camelContext.setDebugger(debugger);
                    }
                    
                    for (Breakpoint breakpoint : breakpoints) {
                        logger.info("Adding Breakpoint [{}] to CamelContext with name [{}].", breakpoint, contextName);
                        debugger.addBreakpoint(breakpoint);
                    }
                }
            });
        }
    }


    /**
     * Handles updating shutdown timeouts on Camel contexts based on {@link ShutdownTimeout}.
     *
     * @param context the initialized Spring context
     * @param testClass the test class being executed
     */
    protected void handleShutdownTimeout(ConfigurableApplicationContext context, Class<?> testClass) throws Exception {
        final int shutdownTimeout;
        final TimeUnit shutdownTimeUnit;
        if (testClass.isAnnotationPresent(ShutdownTimeout.class)) {
            shutdownTimeout = testClass.getAnnotation(ShutdownTimeout.class).value();
            shutdownTimeUnit = testClass.getAnnotation(ShutdownTimeout.class).timeUnit();
        } else {
            shutdownTimeout = 10;
            shutdownTimeUnit = TimeUnit.SECONDS;
        }
        
        CamelSpringTestHelper.doToSpringCamelContexts(context, new DoToSpringCamelContextsStrategy() {
            
            public void execute(String contextName, SpringCamelContext camelContext)
                throws Exception {
                logger.info("Setting shutdown timeout to [{} {}] on CamelContext with name [{}].", new Object[]{shutdownTimeout, shutdownTimeUnit, contextName});
                camelContext.getShutdownStrategy().setTimeout(shutdownTimeout);
                camelContext.getShutdownStrategy().setTimeUnit(shutdownTimeUnit);
            }
        });
    }
    
    /**
     * Handles auto-intercepting of endpoints with mocks based on {@link MockEndpoints}.
     *
     * @param context the initialized Spring context
     * @param testClass the test class being executed
     */
    protected void handleMockEndpoints(ConfigurableApplicationContext context, Class<?> testClass) throws Exception {
        if (testClass.isAnnotationPresent(MockEndpoints.class)) {
            final String mockEndpoints = testClass.getAnnotation(MockEndpoints.class).value();
            CamelSpringTestHelper.doToSpringCamelContexts(context, new DoToSpringCamelContextsStrategy() {
                
                public void execute(String contextName, SpringCamelContext camelContext)
                    throws Exception {
                    logger.info("Enabling auto mocking of endpoints matching pattern [{}] on CamelContext with name [{}].", mockEndpoints, contextName);
                    camelContext.addRegisterEndpointCallback(new InterceptSendToMockEndpointStrategy(mockEndpoints));
                }
            });
        }
    }
    
    /**
     * Handles auto-intercepting of endpoints with mocks based on {@link MockEndpointsAndSkip} and skipping the
     * original endpoint.
     *
     * @param context the initialized Spring context
     * @param testClass the test class being executed
     */
    protected void handleMockEndpointsAndSkip(ConfigurableApplicationContext context, Class<?> testClass) throws Exception {
        if (testClass.isAnnotationPresent(MockEndpointsAndSkip.class)) {
            final String mockEndpoints = testClass.getAnnotation(MockEndpointsAndSkip.class).value();
            CamelSpringTestHelper.doToSpringCamelContexts(context, new DoToSpringCamelContextsStrategy() {
                
                public void execute(String contextName, SpringCamelContext camelContext)
                    throws Exception {
                    // resovle the property place holders of the mockEndpoints 
                    String mockEndpointsValue = camelContext.resolvePropertyPlaceholders(mockEndpoints);
                    logger.info("Enabling auto mocking and skipping of endpoints matching pattern [{}] on CamelContext with name [{}].", mockEndpointsValue, contextName);
                    camelContext.addRegisterEndpointCallback(new InterceptSendToMockEndpointStrategy(mockEndpointsValue, true));
                }
            });
        }
    }
    
    /**
     * Handles override this method to include and override properties with the Camel {@link org.apache.camel.component.properties.PropertiesComponent}.
     *
     * @param context the initialized Spring context
     * @param testClass the test class being executed
     */
    protected void handleUseOverridePropertiesWithPropertiesComponent(ConfigurableApplicationContext context, Class<?> testClass) throws Exception {
        Collection<Method> methods = getAllMethods(testClass);
        final List<Properties> properties = new LinkedList<Properties>();

        for (Method method : methods) {
            if (AnnotationUtils.findAnnotation(method, UseOverridePropertiesWithPropertiesComponent.class) != null) {
                Class<?>[] argTypes = method.getParameterTypes();
                if (argTypes.length > 0) {
                    throw new IllegalArgumentException("Method [" + method.getName()
                            + "] is annotated with UseOverridePropertiesWithPropertiesComponent but is not a no-argument method.");
                } else if (!Properties.class.isAssignableFrom(method.getReturnType())) {
                    throw new IllegalArgumentException("Method [" + method.getName()
                            + "] is annotated with UseOverridePropertiesWithPropertiesComponent but does not return a java.util.Properties.");
                } else if (!Modifier.isStatic(method.getModifiers())) {
                    throw new IllegalArgumentException("Method [" + method.getName()
                            + "] is annotated with UseOverridePropertiesWithPropertiesComponent but is not static.");
                } else if (!Modifier.isPublic(method.getModifiers())) {
                    throw new IllegalArgumentException("Method [" + method.getName()
                            + "] is annotated with UseOverridePropertiesWithPropertiesComponent but is not public.");
                }

                try {
                    properties.add((Properties) method.invoke(null));
                } catch (Exception e) {
                    throw new RuntimeException("Method [" + method.getName()
                            + "] threw exception during evaluation.", e);
                }
            }
        }

        if (properties.size() != 0) {
            CamelSpringTestHelper.doToSpringCamelContexts(context, new DoToSpringCamelContextsStrategy() {
                public void execute(String contextName, SpringCamelContext camelContext) throws Exception {
                    PropertiesComponent pc = camelContext.getComponent("properties", PropertiesComponent.class);
                    Properties extra = new Properties();
                    for (Properties prop : properties) {
                        extra.putAll(prop);
                    }
                    if (!extra.isEmpty()) {
                        logger.info("Using {} properties to override any existing properties on the PropertiesComponent on CamelContext with name [{}].", extra.size(), contextName);
                        pc.setOverrideProperties(extra);
                    }
                }
            });
        }
    }

    /**
     * Handles starting of Camel contexts based on {@link UseAdviceWith} and other state in the JVM.
     *
     * @param context the initialized Spring context
     * @param testClass the test class being executed
     */
    protected void handleCamelContextStartup(ConfigurableApplicationContext context, Class<?> testClass) throws Exception {
        boolean skip = "true".equalsIgnoreCase(System.getProperty("skipStartingCamelContext"));
        if (skip) {
            logger.info("Skipping starting CamelContext(s) as system property skipStartingCamelContext is set to be true.");
        } else if (testClass.isAnnotationPresent(UseAdviceWith.class)) {
            if (testClass.getAnnotation(UseAdviceWith.class).value()) {
                logger.info("Skipping starting CamelContext(s) as UseAdviceWith annotation was found and isUseAdviceWith is set to true.");
                skip = true;
            } else {
                logger.info("Starting CamelContext(s) as UseAdviceWith annotation was found, but isUseAdviceWith is set to false.");
                skip = false;
            }
        }
        
        if (!skip) {
            CamelSpringTestHelper.doToSpringCamelContexts(context, new DoToSpringCamelContextsStrategy() {
                public void execute(String contextName,
                        SpringCamelContext camelContext) throws Exception {
                    logger.info("Starting CamelContext with name [{}].", contextName);
                    camelContext.start();
                }
            });
        }
    }

    /**
     * Returns the class under test in order to enable inspection of annotations while the
     * Spring context is being created.
     * 
     * @return the test class that is being executed
     * @see CamelSpringTestHelper
     */
    protected Class<?> getTestClass() {
        return CamelSpringTestHelper.getTestClass();
    }

}