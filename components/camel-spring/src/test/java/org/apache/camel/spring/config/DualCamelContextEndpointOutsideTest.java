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
package org.apache.camel.spring.config;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spring.SpringTestSupport;
import org.apache.camel.support.service.ServiceHelper;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class DualCamelContextEndpointOutsideTest extends SpringTestSupport {

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/spring/config/DualCamelContextEndpointOutsideTest.xml");
    }

    @Test
    public void testDualCamelContextEndpoint() throws Exception {
        CamelContext camelA = applicationContext.getBean("camel-A", CamelContext.class);
        assertNotNull(camelA);

        CamelContext camelB = applicationContext.getBean("camel-B", CamelContext.class);
        assertNotNull(camelB);

        MockEndpoint mockA = camelA.getEndpoint("mock:mock1", MockEndpoint.class);
        mockA.expectedBodiesReceived("Hello A");

        MockEndpoint mockB = camelB.getEndpoint("mock:mock2", MockEndpoint.class);
        mockB.expectedBodiesReceived("Hello B");

        ProducerTemplate producer1 = camelA.createProducerTemplate();
        producer1.sendBody("direct:start1", "Hello A");

        ProducerTemplate producer2 = camelB.createProducerTemplate();
        producer2.sendBody("direct:start2", "Hello B");

        // make sure we properly stop the services we created
        ServiceHelper.stopService(producer1, producer2);

        mockA.assertIsSatisfied();
        mockB.assertIsSatisfied();
    }

}
