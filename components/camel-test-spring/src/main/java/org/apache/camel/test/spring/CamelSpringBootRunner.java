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

import java.util.List;

import org.junit.runners.model.InitializationError;
import org.springframework.test.context.TestContextManager;
import org.springframework.test.context.TestExecutionListener;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * An implementation bringing the functionality of {@link CamelSpringTestSupport} to
 * Spring Boot Test based test cases.  This approach allows developers to implement tests
 * for their Spring Boot based applications/routes using the typical Spring Test conventions
 * for test development.
 */
public class CamelSpringBootRunner extends SpringJUnit4ClassRunner {

    public CamelSpringBootRunner(Class<?> clazz) throws InitializationError {
        super(clazz);
    }

    /**
     * Returns the specialized manager instance that provides tight integration between Camel testing
     * features and Spring.
     *
     * @return a new instance of {@link CamelTestContextManager}.
     */
    @Override
    protected TestContextManager createTestContextManager(Class<?> clazz) {
        return new CamelTestContextManager(clazz);
    }

    /**
     * An implementation providing additional integration between Spring Test and Camel
     * testing features.
     */
    public static final class CamelTestContextManager extends TestContextManager {

        public CamelTestContextManager(Class<?> testClass) {
            super(testClass);

            // turn off auto starting spring as we need to do this later
            System.setProperty("skipStartingCamelContext", "true");

            // is Camel already registered
            if (!alreadyRegistered()) {
                // inject Camel first, and then disable jmx and add the stop-watch
                List<TestExecutionListener> list = getTestExecutionListeners();
                list.add(0, new CamelSpringTestContextLoaderTestExecutionListener());
                list.add(1, new DisableJmxTestExecutionListener());
                list.add(2, new CamelSpringBootExecutionListener());
                list.add(3, new StopWatchTestExecutionListener());
            }
        }

        private boolean alreadyRegistered() {
            List<TestExecutionListener> list = getTestExecutionListeners();
            if (list != null) {
                for (TestExecutionListener listener : list) {
                    if (listener instanceof CamelSpringTestContextLoaderTestExecutionListener) {
                        return true;
                    }
                }
            }

            return false;
        }

    }

}
