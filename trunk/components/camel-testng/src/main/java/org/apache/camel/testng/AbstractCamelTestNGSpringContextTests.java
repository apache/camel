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
package org.apache.camel.testng;

import org.apache.camel.test.spring.CamelSpringTestContextLoader;
import org.apache.camel.test.spring.CamelSpringTestContextLoaderTestExecutionListener;
import org.apache.camel.test.spring.DisableJmxTestExecutionListener;
import org.apache.camel.test.spring.StopWatchTestExecutionListener;

import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.context.support.DirtiesContextTestExecutionListener;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.context.transaction.TransactionalTestExecutionListener;

import org.testng.IHookable;

/**
 * Bridges Camel testing capabilities from {@link CamelSpringTestSupport} into
 * Spring Test driven classes. This class is based on {@link AbstractTestNGSpringContextTests}
 * but must also declare a {@link ContextConfiguration} to enable the custom context loader needed
 * to support some of the Camel testing capabilities.
 */
@TestExecutionListeners(
                        listeners = {
                                     CamelSpringTestContextLoaderTestExecutionListener.class,
                                     DependencyInjectionTestExecutionListener.class,
                                     DirtiesContextTestExecutionListener.class,
                                     TransactionalTestExecutionListener.class,
                                     DisableJmxTestExecutionListener.class,
                                     StopWatchTestExecutionListener.class},
                         inheritListeners = false)
@ContextConfiguration(loader = AbstractCamelTestNGSpringContextTests.TestNGCamelSpringTestContextLoader.class)
public abstract class AbstractCamelTestNGSpringContextTests extends AbstractTestNGSpringContextTests
        implements IHookable {
    
    public static final class TestNGCamelSpringTestContextLoader extends CamelSpringTestContextLoader {

        /**
         * Since {@link AbstractTestNGSpringContextTests} declares the test context as private and
         * we cannot control its instantiation, we need to use {@link ContextConfiguration} on 
         * {@link AbstractCamelTestNGSpringContextTests}.  Unfortunately, this also tries to load
         * the a context resource based on the default naming convention.  We don't want to require
         * end users to always have a resource for their tests' abstract parent so we override
         * the behavior here to prevent that from happening. 
         */
        @Override
        protected String[] generateDefaultLocations(Class<?> clazz) {
            if (clazz == AbstractCamelTestNGSpringContextTests.class) {
                return new String[0];
            } else {
                return super.generateDefaultLocations(clazz);
            }
        }
        
    }
}

