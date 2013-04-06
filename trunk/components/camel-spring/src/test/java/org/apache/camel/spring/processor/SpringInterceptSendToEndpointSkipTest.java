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
import static org.apache.camel.spring.processor.SpringTestHelper.createSpringCamelContext;

/**
 * @version 
 */
public class SpringInterceptSendToEndpointSkipTest extends ContextTestSupport {

    protected CamelContext createCamelContext() throws Exception {
        return createSpringCamelContext(this, "org/apache/camel/spring/processor/interceptSendToEndpointSkip.xml");
    }

    public void testInterceptEndpointSkip() throws Exception {
        getMockEndpoint("mock:bar").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:detour").expectedBodiesReceived("Bye World");
        // mock:foo is skipped so no messages received
        getMockEndpoint("mock:foo").expectedMessageCount(0);
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye World");

        template.sendBody("direct:third", "Hello World");

        assertMockEndpointsSatisfied();
    }

}