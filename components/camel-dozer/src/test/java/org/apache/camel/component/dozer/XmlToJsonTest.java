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

import java.io.InputStream;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;

@CamelSpringTest
@ContextConfiguration
public class XmlToJsonTest {

    private static final String ABC_ORDER_PATH = "org/apache/camel/component/dozer/abc-order.xml";
    private static final String XYZ_ORDER_PATH = "org/apache/camel/component/dozer/xyz-order.json";

    @EndpointInject("mock:result")
    private MockEndpoint resultEndpoint;

    @Produce("direct:start")
    private ProducerTemplate startEndpoint;

    @Autowired
    private CamelContext camelContext;

    @AfterEach
    public void tearDown() {
        resultEndpoint.reset();
    }

    @Test
    void testXmlToJson() throws Exception {
        resultEndpoint.expectedMessageCount(1);
        startEndpoint.sendBody(getResourceAsString(ABC_ORDER_PATH));
        // check results
        resultEndpoint.assertIsSatisfied();
        String result = resultEndpoint.getExchanges().get(0).getIn().getBody(String.class);
        assertEquals(getResourceAsString(XYZ_ORDER_PATH), result);
    }

    @Test
    void testMultipleSends() throws Exception {
        resultEndpoint.expectedMessageCount(2);
        startEndpoint.sendBody(getResourceAsString(ABC_ORDER_PATH));
        startEndpoint.sendBody(getResourceAsString(ABC_ORDER_PATH));
        // check results
        resultEndpoint.assertIsSatisfied();
        String result1 = resultEndpoint.getExchanges().get(0).getIn().getBody(String.class);
        String result2 = resultEndpoint.getExchanges().get(1).getIn().getBody(String.class);
        assertEquals(getResourceAsString(XYZ_ORDER_PATH), result1);
        assertEquals(getResourceAsString(XYZ_ORDER_PATH), result2);
    }

    private String getResourceAsString(String resourcePath) {
        InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath);
        return camelContext.getTypeConverter().convertTo(String.class, is);
    }
}
