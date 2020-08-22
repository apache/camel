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
package org.apache.camel.component.dozer;

import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.dozer.example.abc.ABCOrder;
import org.apache.camel.component.dozer.example.abc.ABCOrder.Header;
import org.apache.camel.component.dozer.example.xyz.XYZOrder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ContextConfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;

@CamelSpringTest
@ContextConfiguration
public class CustomMappingTest {

    @EndpointInject("mock:result")
    private MockEndpoint resultEndpoint;

    @Produce("direct:start")
    private ProducerTemplate startEndpoint;

    @AfterEach
    public void tearDown() {
        resultEndpoint.reset();
    }

    @Test
    void testCustomMapping() throws Exception {
        resultEndpoint.expectedMessageCount(1);
        ABCOrder abcOrder = new ABCOrder();
        abcOrder.setHeader(new Header());
        abcOrder.getHeader().setStatus("GOLD");
        abcOrder.getHeader().setCustomerNum("ACME");
        startEndpoint.sendBody(abcOrder);
        // check results
        resultEndpoint.assertIsSatisfied();
        XYZOrder result = resultEndpoint.getExchanges().get(0).getIn().getBody(XYZOrder.class);
        assertEquals("custom2:GOLD", result.getPriority());
        assertEquals("mapCustomer:ACME", result.getCustId());
    }
}
