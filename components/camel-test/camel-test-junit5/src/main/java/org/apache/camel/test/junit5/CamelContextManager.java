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

import org.apache.camel.ConsumerTemplate;
import org.apache.camel.FluentProducerTemplate;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.Service;
import org.apache.camel.model.ModelCamelContext;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * A ContextManager is used to manage the lifecycle of a {@link org.apache.camel.CamelContext} during test execution
 */
public interface CamelContextManager {

    /**
     * Creates a new CamelContext
     *
     * @param  test      the test instance requesting the next context
     * @throws Exception if unable to create the context
     */
    void createCamelContext(Object test) throws Exception;

    /**
     * A callback method to be executed before starting the context
     *
     * @param  test
     * @throws Exception
     */
    void beforeContextStart(Object test) throws Exception;

    /**
     * Gets the reference to the CamelContext instance
     *
     * @return the CamelContext instance
     */
    ModelCamelContext context();

    /**
     * Gets the reference to the producer template created during initialization
     *
     * @return the producer template instance
     */
    ProducerTemplate template();

    /**
     * Gets the reference to the fluent producer template created during initialization
     *
     * @return the fluent producer template instance
     */
    FluentProducerTemplate fluentTemplate();

    /**
     * Gets the reference to the consumer template created during initialization
     *
     * @return the consumer template instance
     */
    ConsumerTemplate consumer();

    /**
     * When a separate service is used to manage the context lifecycle, this returns the reference to that service
     *
     * @return the reference to the context lifecycle service
     */
    @Deprecated(since = "4.7.0")
    Service camelContextService();

    /**
     * Starts the context
     *
     * @throws Exception if unable to start the context for any reason
     */
    void startCamelContext() throws Exception;

    /**
     * Stops the context
     */
    void stopCamelContext();

    /**
     * Stops the templates
     */
    void stopTemplates();

    /**
     * Stops the manager (typically run after every test execution)
     */
    void stop();

    /**
     * Close the manager (this is run after all tests have been executed)
     */
    void close();

    /**
     * Sets the JUnit's data context that may be used to provide additional information for some tests
     *
     * @param globalStore JUnit's data context instance
     */
    void setGlobalStore(ExtensionContext.Store globalStore);

    /**
     * Dumps the route coverage information
     */
    void dumpRouteCoverage(Class<?> clazz, String currentTestName, long time) throws Exception;

    /**
     * Dumps the route
     */
    void dumpRoute(Class<?> clazz, String currentTestName, String format) throws Exception;
}
