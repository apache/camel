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

import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.runners.model.InitializationError;
import org.springframework.test.context.TestContextManager;
import org.springframework.test.context.TestExecutionListener;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * An implementation bringing the functionality of {@link org.apache.camel.test.spring.CamelSpringTestSupport} to
 * Spring Test based test cases.  This approach allows developers to implement tests
 * for their Spring based applications/routes using the typical Spring Test conventions
 * for test development.
 */
public class CamelSpringJUnit4ClassRunner extends SpringJUnit4ClassRunner {

    public CamelSpringJUnit4ClassRunner(Class<?> clazz) throws InitializationError {
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
        return new CamelTestContextManager(clazz, getDefaultContextLoaderClassName(clazz));
    }

    /**
     * Returns the specialized loader for tight integration between Camel testing features
     * and the application context initialization.
     *
     * @return Returns the class name for {@link org.apache.camel.test.spring.CamelSpringTestContextLoader}
     */
    @Override
    protected String getDefaultContextLoaderClassName(Class<?> clazz) {
        return CamelSpringTestContextLoader.class.getName();
    }

    /**
     * An implementation providing additional integration between Spring Test and Camel
     * testing features.
     */
    public static final class CamelTestContextManager extends TestContextManager {

        public CamelTestContextManager(Class<?> testClass, String defaultContextLoaderClassName) {
            super(testClass, defaultContextLoaderClassName);
        }

        /**
         * Augments the default listeners with additional listeners to provide support
         * for the Camel testing features.
         */
        @Override
        protected Set<Class<? extends TestExecutionListener>> getDefaultTestExecutionListenerClasses() {
            Set<Class<? extends TestExecutionListener>> classes = new LinkedHashSet<Class<? extends TestExecutionListener>>();

            classes.add(CamelSpringTestContextLoaderTestExecutionListener.class);
            classes.addAll(super.getDefaultTestExecutionListenerClasses());
            classes.add(DisableJmxTestExecutionListener.class);
            classes.add(StopWatchTestExecutionListener.class);

            return classes;
        }
    }

}
