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

import org.springframework.core.Ordered;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

/**
 * Helper for {@link CamelSpringTestContextLoader} that sets the test class state
 * in {@link CamelSpringTestHelper} almost immediately before the loader initializes
 * the Spring context.
 * <p/>
 * Implemented as a listener as the state can be set on a {@code ThreadLocal} and we are pretty sure
 * that the same thread will be used to initialize the Spring context.
 */
public class CamelSpringTestContextLoaderTestExecutionListener extends AbstractTestExecutionListener {

    /**
     * The default implementation returns {@link org.springframework.core.Ordered#LOWEST_PRECEDENCE},
     * thereby ensuring that custom listeners are ordered after default
     * listeners supplied by the framework. Can be overridden by subclasses
     * as necessary.
     */
    @Override
    public int getOrder() {
        //set Camel first
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public void prepareTestInstance(TestContext testContext) throws Exception {
        CamelSpringTestHelper.setTestClass(testContext.getTestClass());
        CamelSpringTestHelper.setTestContext(testContext);
    }
}
