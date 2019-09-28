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

import org.apache.camel.api.management.JmxSystemPropertyKeys;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

/**
 * Provides reset to pre-test state behavior for global enable/disable of JMX
 * support in Camel through the use of {@link DisableJmx}. Tries to ensure that
 * the pre-test value is restored.
 */
public class DisableJmxTestExecutionListener extends AbstractTestExecutionListener {

    @Override
    public void afterTestClass(TestContext testContext) throws Exception {
        if (CamelSpringTestHelper.getOriginalJmxDisabled() == null) {
            System.clearProperty(JmxSystemPropertyKeys.DISABLED);
        } else {
            System.setProperty(JmxSystemPropertyKeys.DISABLED, CamelSpringTestHelper.getOriginalJmxDisabled());
        }
    }

    /**
     * Returns the precedence that is used by Spring to choose the appropriate
     * execution order of test listeners.
     * 
     * See {@link SpringTestExecutionListenerSorter#getPrecedence(Class)} for more.
     */
    @Override
    public int getOrder() {
        return SpringTestExecutionListenerSorter.getPrecedence(getClass());
    }

}
