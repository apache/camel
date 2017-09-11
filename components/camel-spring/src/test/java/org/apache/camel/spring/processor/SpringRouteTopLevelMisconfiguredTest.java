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
package org.apache.camel.spring.processor;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.RuntimeCamelException;

import static org.apache.camel.spring.processor.SpringTestHelper.createSpringCamelContext;

public class SpringRouteTopLevelMisconfiguredTest extends ContextTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        try {
            createSpringCamelContext(this, "org/apache/camel/spring/processor/SpringRouteTopLevelOnExceptionMisconfiguredTest.xml");
            fail("Should have thrown exception");
        } catch (RuntimeCamelException e) {
            IllegalArgumentException iae = assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
            assertTrue(iae.getMessage().startsWith("The output must be added as top-level on the route."));
        }

        try {
            createSpringCamelContext(this, "org/apache/camel/spring/processor/SpringRouteTopLevelOnCompletionMisconfiguredTest.xml");
            fail("Should have thrown exception");
        } catch (RuntimeCamelException e) {
            IllegalArgumentException iae = assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
            assertTrue(iae.getMessage().startsWith("The output must be added as top-level on the route."));
        }

        try {
            createSpringCamelContext(this, "org/apache/camel/spring/processor/SpringRouteTopLevelTransactedMisconfiguredTest.xml");
            fail("Should have thrown exception");
        } catch (RuntimeCamelException e) {
            IllegalArgumentException iae = assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
            assertTrue(iae.getMessage().startsWith("The output must be added as top-level on the route."));
        }


        // return a working context instead, to let this test pass
        return createSpringCamelContext(this, "org/apache/camel/spring/processor/SpringTryProcessorHandledTest.xml");
    }

    public void testMisconfigured() {
        // noop
    }

}