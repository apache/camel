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
package org.apache.camel.component.dozer;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.dozer.example.abc.ABCOrder;
import org.apache.camel.component.dozer.example.xyz.XYZOrder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
public class ExpressionMappingClasspathTest {
    
    @EndpointInject(uri = "mock:result")
    private MockEndpoint resultEndpoint;
    
    @Produce(uri = "direct:start")
    private ProducerTemplate startEndpoint;
    
    @Autowired
    private CamelContext camelContext;
    
    @After
    public void tearDown() {
        resultEndpoint.reset();
    }

    @Test
    public void testExpressionMappingScript() throws Exception {
        resultEndpoint.expectedMessageCount(1);
        Map<String, Object> headers = new HashMap<String, Object>();
        final String customerNumber = "CAFE-345";
        final String orderNumber = "ABC-001";
        headers.put("customerNumber", customerNumber);
        headers.put("orderNumber", orderNumber);
        ABCOrder abcOrder = new ABCOrder();
        // Header value should be mapped to custId in target model
        startEndpoint.sendBodyAndHeaders(abcOrder, headers);
        // check results
        resultEndpoint.assertIsSatisfied();
        XYZOrder result = resultEndpoint.getExchanges().get(0).getIn().getBody(XYZOrder.class);
        Assert.assertEquals(customerNumber, result.getCustId());
        Assert.assertEquals(orderNumber, result.getOrderId());
    }
}
