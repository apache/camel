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
package org.apache.camel.spring.postprocessor;

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spring.SpringTestSupport;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @version 
 */
public class CamelCustomPostProcessorOnRouteBuilderTest extends SpringTestSupport {

    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/spring/postprocessor/camelCustomPostProcessorOnRouteBuilderTest.xml");
    }

    public void testShouldProcessAnnotatedFields() throws Exception {
        getMockEndpoint("mock:injected").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        TestPojo pojo = (TestPojo) context.getRegistry().lookupByName("testPojo");
        assertNotNull("Test pojo not registered", pojo);

        assertEquals("Processor has not changed field value", "Changed Value", pojo.getTestValue());

        // and test that the injected camel annotation on TestPojo also works
        MockEndpoint foo = getMockEndpoint("mock:foo");
        foo.expectedBodiesReceived("Hi");

        pojo.sendToFoo("Hi");

        foo.assertIsSatisfied();
    }

}
