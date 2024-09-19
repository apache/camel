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

package org.apache.camel.test.junit5;

import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.FluentProducerTemplate;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.Service;
import org.apache.camel.model.ModelCamelContext;

/**
 * Base test class, mostly made of legacy setup methods. This is intended for internal use.
 */
public abstract class AbstractTestSupport implements CommonTestSupport {
    protected final TestExecutionConfiguration testConfigurationBuilder;
    protected final CamelContextConfiguration camelContextConfiguration;
    protected volatile ModelCamelContext context;
    protected volatile ProducerTemplate template;
    protected volatile FluentProducerTemplate fluentTemplate;
    protected volatile ConsumerTemplate consumer;

    protected AbstractTestSupport() {
        testConfigurationBuilder = new TestExecutionConfiguration();
        camelContextConfiguration = new CamelContextConfiguration();
    }

    protected AbstractTestSupport(TestExecutionConfiguration testConfigurationBuilder,
                                  CamelContextConfiguration camelContextConfiguration) {
        this.testConfigurationBuilder = testConfigurationBuilder;
        this.camelContextConfiguration = camelContextConfiguration;
    }

    /**
     * Strategy to set up resources, before {@link CamelContext} is created. This is meant to be used by resources that
     * must be available before the context is created. Do not use this as a replacement for tasks that can be handled
     * using JUnit's annotations.
     */
    protected void setupResources() throws Exception {
        // noop
    }

    /**
     * Strategy to cleanup resources, after {@link CamelContext} is stopped
     */
    protected void cleanupResources() throws Exception {
        // noop
    }

    @Deprecated(since = "4.7.0")
    public Service getCamelContextService() {
        return camelContextConfiguration.camelContextService();
    }

    @Deprecated(since = "4.7.0")
    public Service camelContextService() {
        return camelContextConfiguration.camelContextService();
    }

    /**
     * Gets a reference to the CamelContext. Must not be used during test setup.
     *
     * @return A reference to the CamelContext
     */
    public CamelContext context() {
        return context;
    }

    /**
     * Sets the CamelContext. Used by the manager to override tests that try to access the context during setup. DO NOT
     * USE.
     *
     * @param context
     */
    @Deprecated(since = "4.7.0")
    public void setContext(ModelCamelContext context) {
        this.context = context;
    }

    public ProducerTemplate template() {
        return template;
    }

    public FluentProducerTemplate fluentTemplate() {
        return fluentTemplate;
    }

    public ConsumerTemplate consumer() {
        return consumer;
    }

    /**
     * Allows a service to be registered a separate lifecycle service to start and stop the context; such as for Spring
     * when the ApplicationContext is started and stopped, rather than directly stopping the CamelContext
     */
    public void setCamelContextService(Service service) {
        camelContextConfiguration.withCamelContextService(service);
    }

    /**
     * Gets the {@link CamelContextConfiguration} for the test
     *
     * @return the camel context configuration
     */
    @Override
    public final CamelContextConfiguration camelContextConfiguration() {
        return camelContextConfiguration;
    }

    /**
     * Gets the {@link TestExecutionConfiguration} test execution configuration instance for the test
     *
     * @return the configuration instance for the test
     */
    @Override
    public final TestExecutionConfiguration testConfiguration() {
        return testConfigurationBuilder;
    }
}
