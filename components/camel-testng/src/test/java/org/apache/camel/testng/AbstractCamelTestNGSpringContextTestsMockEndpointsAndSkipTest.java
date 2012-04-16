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

import org.apache.camel.EndpointInject;
import org.apache.camel.ServiceStatus;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.MockEndpointsAndSkip;
import org.testng.annotations.Test;

import static org.junit.Assert.assertEquals;

@MockEndpointsAndSkip("mock:c")
public class AbstractCamelTestNGSpringContextTestsMockEndpointsAndSkipTest
        extends AbstractCamelTestNGSpringContextTestsPlainTest {

    @EndpointInject(uri = "mock:mock:c", context = "camelContext2")
    protected MockEndpoint mockMockC;
    
    @EndpointInject(uri = "mock:c", context = "camelContext2")
    protected MockEndpoint mockC;
    
    @Test
    @Override
    public void testPositive() throws Exception {
        assertEquals(ServiceStatus.Started, camelContext.getStatus());
        assertEquals(ServiceStatus.Started, camelContext2.getStatus());
        
        mockA.expectedBodiesReceived("David");
        mockB.expectedBodiesReceived("Hello David");
        mockC.expectedMessageCount(0);
        mockMockC.expectedBodiesReceived("Hello David");
        
        start.sendBody("David");
        start2.sendBody("David");
        
        MockEndpoint.assertIsSatisfied(camelContext);
    }
}
