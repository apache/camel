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
package org.apache.camel.dataformat.bindy.csv;

import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dataformat.bindy.model.simple.oneclass.Order;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

@ContextConfiguration
public class BindySingleQuoteStartWordCsvUnmarshallTest extends AbstractJUnit4SpringContextTests {

    private static final String URI_MOCK_RESULT = "mock:result";
    private static final String URI_DIRECT_START = "direct:start";

    @Produce(URI_DIRECT_START)
    private ProducerTemplate template;

    @EndpointInject(URI_MOCK_RESULT)
    private MockEndpoint result;

    private String expected;

    @Test
    @DirtiesContext
    public void testUnMarshallMessage() throws Exception {

        expected = "10,A9,Patric,'T jo,ISIN,XD12345678,BUY,Share,2500.45,USD,08-01-2009";

        template.sendBody(expected);

        result.expectedMessageCount(1);
        result.assertIsSatisfied();

        Order order = result.getReceivedExchanges().get(0).getIn().getBody(Order.class);
        Assert.assertEquals(10, order.getOrderNr());
        Assert.assertEquals("Patric", order.getFirstName());
        Assert.assertEquals("'T jo", order.getLastName());
    }

    public static class ContextConfig extends RouteBuilder {
        BindyCsvDataFormat camelDataFormat = new BindyCsvDataFormat(org.apache.camel.dataformat.bindy.model.simple.oneclass.Order.class);

        @Override
        public void configure() {
            from(URI_DIRECT_START).unmarshal(camelDataFormat).to(URI_MOCK_RESULT);
        }

    }
}
