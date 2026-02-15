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
package org.apache.camel.component.smooks.routing;

import org.apache.camel.CamelContext;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dataformat.smooks.Customer;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.smooks.Smooks;
import org.smooks.io.source.StreamSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BeanRouterFunctionalTest extends CamelTestSupport {
    @Test
    public void test() throws Exception {
        MockEndpoint endpoint = createAndConfigureMockEndpoint("mock://beanRouterUnitTest");
        endpoint.setExpectedMessageCount(1);

        try (Smooks smooks = new Smooks("bean-routing-smooks-config.xml")) {
            smooks.getApplicationContext().getRegistry().registerObject(CamelContext.class, endpoint.getCamelContext());
            smooks.filterSource(new StreamSource<>(getClass().getResourceAsStream("/xml/customer.xml")));

            endpoint.assertIsSatisfied();
            Customer customer = endpoint.getReceivedExchanges().get(0).getMessage().getBody(Customer.class);
            assertEquals("Wonderland", customer.getCountry());
        }
    }

    private MockEndpoint createAndConfigureMockEndpoint(String endpointUri) throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint(endpointUri);
        return mockEndpoint;
    }
}
