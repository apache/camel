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

import org.apache.camel.CamelContext;
import org.apache.camel.spring.SpringCamelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

public class CamelSpringBootExecutionListener extends AbstractTestExecutionListener {

    private static final Logger LOG = LoggerFactory.getLogger(CamelSpringBootExecutionListener.class);

    @Override
    public void prepareTestInstance(TestContext testContext) throws Exception {
        LOG.info("@RunWith(CamelSpringBootRunner.class) preparing: {}", testContext.getTestClass());

        Class<?> testClass = testContext.getTestClass();
        // we are customizing the Camel context with
        // CamelAnnotationsHandler so we do not want to start it
        // automatically, which would happen when SpringCamelContext
        // is added to Spring ApplicationContext, so we set the flag
        // not to start it just yet
        SpringCamelContext.setNoStart(true);
        System.setProperty("skipStartingCamelContext", "true");
        ConfigurableApplicationContext context = (ConfigurableApplicationContext) testContext.getApplicationContext();

        // Post CamelContext(s) instantiation but pre CamelContext(s) start setup
        CamelAnnotationsHandler.handleProvidesBreakpoint(context, testClass);
        CamelAnnotationsHandler.handleShutdownTimeout(context, testClass);
        CamelAnnotationsHandler.handleMockEndpoints(context, testClass);
        CamelAnnotationsHandler.handleMockEndpointsAndSkip(context, testClass);
        CamelAnnotationsHandler.handleUseOverridePropertiesWithPropertiesComponent(context, testClass);

        System.clearProperty("skipStartingCamelContext");
        SpringCamelContext.setNoStart(false);
    }

    @Override
    public void beforeTestMethod(TestContext testContext) throws Exception {
        LOG.info("@RunWith(CamelSpringBootRunner.class) before: {}.{}", testContext.getTestClass(), testContext.getTestMethod().getName());

        Class<?> testClass = testContext.getTestClass();
        String testName = testContext.getTestMethod().getName();

        ConfigurableApplicationContext context = (ConfigurableApplicationContext) testContext.getApplicationContext();

        // mark Camel to be startable again and start Camel
        System.clearProperty("skipStartingCamelContext");

        // route coverage need to know the test method
        CamelAnnotationsHandler.handleRouteCoverage(context, testClass, s -> testName);

        LOG.info("Initialized CamelSpringBootRunner now ready to start CamelContext");
        CamelAnnotationsHandler.handleCamelContextStartup(context, testClass);
    }

}
