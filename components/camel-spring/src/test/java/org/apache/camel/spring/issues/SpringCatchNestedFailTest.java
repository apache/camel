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
package org.apache.camel.spring.issues;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.junit.Test;

import static org.apache.camel.spring.processor.SpringTestHelper.createSpringCamelContext;

public class SpringCatchNestedFailTest extends ContextTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        return createSpringCamelContext(this, "org/apache/camel/spring/issues/SpringCatchNestedFailTest.xml");
    }

    @Test
    public void testOk() throws Exception {
        getMockEndpoint("mock:bar").expectedMessageCount(1);
        getMockEndpoint("mock:donkey").expectedMessageCount(0);
        getMockEndpoint("mock:catch").expectedMessageCount(0);
        getMockEndpoint("mock:kong").expectedMessageCount(0);
        getMockEndpoint("mock:catchEnd").expectedMessageCount(0);
        getMockEndpoint("mock:end").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello Camel");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testFail() throws Exception {
        getMockEndpoint("mock:bar").expectedMessageCount(0);
        getMockEndpoint("mock:donkey").expectedMessageCount(1);
        getMockEndpoint("mock:catch").expectedMessageCount(1);
        getMockEndpoint("mock:kong").expectedMessageCount(0);
        getMockEndpoint("mock:catchEnd").expectedMessageCount(1);
        getMockEndpoint("mock:end").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello Donkey");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testFailAgain() throws Exception {
        getMockEndpoint("mock:bar").expectedMessageCount(0);
        getMockEndpoint("mock:donkey").expectedMessageCount(1);
        getMockEndpoint("mock:catch").expectedMessageCount(1);
        getMockEndpoint("mock:kong").expectedMessageCount(1);
        getMockEndpoint("mock:catchEnd").expectedMessageCount(0);
        getMockEndpoint("mock:end").expectedMessageCount(0);

        try {
            template.sendBody("direct:start", "Donkey Kong");
            fail("Should have thrown exception");
        } catch (CamelExecutionException e) {
            assertIsInstanceOf(IllegalStateException.class, e.getCause());
            assertEquals("Damn Kong", e.getCause().getMessage());
        }

        assertMockEndpointsSatisfied();
    }

}
