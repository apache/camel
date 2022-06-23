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

import org.apache.camel.spring.SpringCamelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

public class CamelSpringBootExecutionListener extends AbstractTestExecutionListener {

    protected static ThreadLocal<ConfigurableApplicationContext> threadApplicationContext = new ThreadLocal<>();

    private static final Logger LOG = LoggerFactory.getLogger(CamelSpringBootExecutionListener.class);
    private static final String PROPERTY_SKIP_STARTING_CAMEL_CONTEXT = "skipStartingCamelContext";

    /**
     * Returns the precedence that is used by Spring to choose the appropriate execution order of test listeners.
     *
     * See {@link SpringTestExecutionListenerSorter#getPrecedence(Class)} for more.
     */
    @Override
    public int getOrder() {
        return SpringTestExecutionListenerSorter.getPrecedence(getClass());
    }

    @Override
    public void beforeTestClass(TestContext testContext) throws Exception {
        // prevent other extensions to start the Camel context
        preventContextStart();
    }

    @Override
    public void prepareTestInstance(TestContext testContext) throws Exception {
        LOG.info("CamelSpringBootExecutionListener preparing: {}", testContext.getTestClass());

        Class<?> testClass = testContext.getTestClass();

        // need to prepare this before we load spring application context
        CamelAnnotationsHandler.handleDisableJmx(testClass);
        CamelAnnotationsHandler.handleExcludeRoutes(testClass);

        // prevent the Camel context to be started to be able to extend it.
        preventContextStart();
        ConfigurableApplicationContext context = (ConfigurableApplicationContext) testContext.getApplicationContext();

        CamelAnnotationsHandler.handleUseOverridePropertiesWithPropertiesComponent(context, testClass);

        // Post CamelContext(s) instantiation but pre CamelContext(s) start
        // setup
        CamelAnnotationsHandler.handleProvidesBreakpoint(context, testClass);
        CamelAnnotationsHandler.handleShutdownTimeout(context, testClass);
        CamelAnnotationsHandler.handleMockEndpoints(context, testClass);
        CamelAnnotationsHandler.handleMockEndpointsAndSkip(context, testClass);

        System.clearProperty(PROPERTY_SKIP_STARTING_CAMEL_CONTEXT);
        SpringCamelContext.setNoStart(false);
    }

    /**
     * Sets the {@link SpringCamelContext#setNoStart(boolean)} and the system property
     * <code>skipStartingCamelContext</code>to <code>true</code> to let us customizing the Camel context with
     * {@link CamelAnnotationsHandler} before it has been started. It's needed as early as possible to prevent other
     * extensions to start it <b>and</b> before every test run.
     */
    private void preventContextStart() {
        SpringCamelContext.setNoStart(true);
        System.setProperty(PROPERTY_SKIP_STARTING_CAMEL_CONTEXT, "true");
    }

    @Override
    public void beforeTestMethod(TestContext testContext) throws Exception {
        LOG.info("CamelSpringBootExecutionListener before: {}.{}", testContext.getTestClass(),
                testContext.getTestMethod().getName());

        Class<?> testClass = testContext.getTestClass();
        String testName = testContext.getTestMethod().getName();

        ConfigurableApplicationContext context = (ConfigurableApplicationContext) testContext.getApplicationContext();
        threadApplicationContext.set(context);

        // mark Camel to be startable again and start Camel
        System.clearProperty(PROPERTY_SKIP_STARTING_CAMEL_CONTEXT);

        // route coverage need to know the test method
        CamelAnnotationsHandler.handleRouteCoverage(context, testClass, s -> testName);

        LOG.info("Initialized CamelSpringBootExecutionListener now ready to start CamelContext");
        CamelAnnotationsHandler.handleCamelContextStartup(context, testClass);
    }

    @Override
    public void afterTestMethod(TestContext testContext) throws Exception {
        LOG.info("CamelSpringBootExecutionListener after: {}.{}", testContext.getTestClass(),
                testContext.getTestMethod().getName());

        Class<?> testClass = testContext.getTestClass();
        String testName = testContext.getTestMethod().getName();

        ConfigurableApplicationContext context = threadApplicationContext.get();
        if (context != null && context.isRunning()) {
            // dump route coverage for each test method so its accurate
            // statistics
            // even if spring application context is running (i.e. its not
            // dirtied per test method)
            CamelAnnotationsHandler.handleRouteCoverageDump(context, testClass, s -> testName);
        }
    }
}
