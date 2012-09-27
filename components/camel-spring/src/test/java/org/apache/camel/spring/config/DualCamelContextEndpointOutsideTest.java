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
package org.apache.camel.spring.config;

import org.apache.camel.CamelContext;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spring.SpringTestSupport;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @version 
 */
public class DualCamelContextEndpointOutsideTest extends SpringTestSupport {

    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/spring/config/DualCamelContextEndpointOutsideTest.xml");
    }

    public void testDualCamelContextEndpoint() throws Exception {
        CamelContext camelA = applicationContext.getBean("camel-A", CamelContext.class);
        assertNotNull(camelA);

        CamelContext camelB = applicationContext.getBean("camel-B", CamelContext.class);
        assertNotNull(camelB);

        MockEndpoint mockA = camelA.getEndpoint("mock:mock1", MockEndpoint.class);
        mockA.expectedBodiesReceived("Hello A");

        MockEndpoint mockB = camelB.getEndpoint("mock:mock2", MockEndpoint.class);
        mockB.expectedBodiesReceived("Hello B");

        camelA.createProducerTemplate().sendBody("direct:start1", "Hello A");
        camelB.createProducerTemplate().sendBody("direct:start2", "Hello B");

        mockA.assertIsSatisfied();
        mockB.assertIsSatisfied();
    }

}
