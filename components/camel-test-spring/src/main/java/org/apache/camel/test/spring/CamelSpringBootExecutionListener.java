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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

public class CamelSpringBootExecutionListener extends AbstractTestExecutionListener {

    private static final Logger LOG = LoggerFactory.getLogger(CamelSpringBootExecutionListener.class);

    @Override
    public void prepareTestInstance(TestContext testContext) throws Exception {
        LOG.info("@RunWith(CamelSpringBootJUnit4ClassRunner.class) preparing: {}", testContext.getTestClass());

        Class<?> testClass = testContext.getTestClass();
        ConfigurableApplicationContext context = (ConfigurableApplicationContext) testContext.getApplicationContext();

        // Post CamelContext(s) instantiation but pre CamelContext(s) start setup
        CamelAnnotationsHandler.handleProvidesBreakpoint(context, testClass);
        CamelAnnotationsHandler.handleShutdownTimeout(context, testClass);
        CamelAnnotationsHandler.handleMockEndpoints(context, testClass);
        CamelAnnotationsHandler.handleMockEndpointsAndSkip(context, testClass);
        CamelAnnotationsHandler.handleUseOverridePropertiesWithPropertiesComponent(context, testClass);
    }

    @Override
    public void beforeTestMethod(TestContext testContext) throws Exception {
        LOG.info("@RunWith(CamelSpringBootJUnit4ClassRunner.class) before: {}.{}", testContext.getTestClass(), testContext.getTestMethod().getName());

        Class<?> testClass = testContext.getTestClass();
        ConfigurableApplicationContext context = (ConfigurableApplicationContext) testContext.getApplicationContext();

        // mark Camel to be startable again and start Camel
        System.clearProperty("skipStartingCamelContext");

        LOG.info("Initialized CamelSpringBootJUnit4ClassRunner now ready to start CamelContext");
        CamelAnnotationsHandler.handleCamelContextStartup(context, testClass);
    }

}
