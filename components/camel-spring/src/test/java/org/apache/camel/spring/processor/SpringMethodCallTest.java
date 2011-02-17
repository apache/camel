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
import org.apache.camel.component.mock.MockEndpoint;

import static org.apache.camel.spring.processor.SpringTestHelper.createSpringCamelContext;

/**
 * @version 
 */
public class SpringMethodCallTest extends ContextTestSupport {

    public void testMethodCallType() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Camel");
        mock.expectedHeaderReceived("foo", "Hi Camel");

        template.sendBody("direct:start", "Camel");

        assertMockEndpointsSatisfied();
    }

    public void testMethodCallRef() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:ref");
        mock.expectedBodiesReceived("Camel");
        mock.expectedHeaderReceived("foo", "Hi Camel");

        template.sendBody("direct:ref", "Camel");

        assertMockEndpointsSatisfied();
    }

    public void testToBeanType() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hi Camel", "Hi World");

        template.sendBody("direct:tobeantype", "Camel");
        template.sendBody("direct:tobeantype", "World");

        assertMockEndpointsSatisfied();
    }

    protected CamelContext createCamelContext() throws Exception {
        return createSpringCamelContext(this, "/org/apache/camel/spring/processor/SpringMethodCallTest.xml");
    }

}
