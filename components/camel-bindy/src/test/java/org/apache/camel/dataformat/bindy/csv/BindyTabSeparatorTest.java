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

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dataformat.bindy.model.tab.PurchaseOrder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class BindyTabSeparatorTest extends CamelTestSupport {

    @Test
    public void testUnmarshal() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:unmarshal");
        mock.expectedMessageCount(1);

        template.sendBody("direct:unmarshal", "123\tCamel in Action\t2\tPlease hurry\tJane Doe\tJohn Doe\n");

        assertMockEndpointsSatisfied();

        PurchaseOrder order = mock.getReceivedExchanges().get(0).getIn().getBody(PurchaseOrder.class);
        
        assertEquals(123, order.getId());
        assertEquals("Camel in Action", order.getName());
        assertEquals(2, order.getAmount());
        assertEquals("Please hurry", order.getOrderText());
        assertEquals("Jane Doe", order.getSalesRef());
        assertEquals("John Doe", order.getCustomerRef());
    }

    @Test
    public void testMarshal() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:marshal");
        mock.expectedBodiesReceived("123\tCamel in Action\t2\tPlease hurry\tJane Doe\tJohn Doe\n");

        PurchaseOrder order = new PurchaseOrder();
        order.setId(123);
        order.setName("Camel in Action");
        order.setAmount(2);
        order.setOrderText("Please hurry");
        order.setSalesRef("Jane Doe");
        order.setCustomerRef("John Doe");

        template.sendBody("direct:marshal", order);

        assertMockEndpointsSatisfied();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testUnmarshalEmptyTrailingNoneRequiredFields() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:unmarshal");
        mock.expectedMessageCount(1);

        template.sendBodyAndHeader("direct:unmarshal",
                "123\tCamel in Action\t2\t\t\n456\tCamel in Action\t1\t\t\t\n"
                        + "456\tCamel in Action\t2\t\t\n456\tCamel in Action\t1\t\t\t\n", Exchange.CONTENT_ENCODING, "iso8859-1");

        assertMockEndpointsSatisfied();

              
        List<PurchaseOrder> orders = (List<PurchaseOrder>)mock.getReceivedExchanges().get(0).getIn().getBody();
        PurchaseOrder order = orders.get(0);
        
        assertEquals(123, order.getId());
        assertEquals("Camel in Action", order.getName());
        assertEquals(2, order.getAmount());
        assertNull(order.getOrderText());
        assertNull(order.getSalesRef());
        assertNull(order.getCustomerRef());
    }

    @Test
    public void testMarshalEmptyTrailingNoneRequiredFields() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:marshal");
        mock.expectedBodiesReceived("123\tCamel in Action\t2\t\t\t\n");

        PurchaseOrder order = new PurchaseOrder();
        order.setId(123);
        order.setName("Camel in Action");
        order.setAmount(2);
        order.setOrderText("");
        order.setSalesRef("");
        order.setCustomerRef("");

        template.sendBody("direct:marshal", order);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                BindyCsvDataFormat bindy = new BindyCsvDataFormat(org.apache.camel.dataformat.bindy.model.tab.PurchaseOrder.class);

                from("direct:marshal")
                        .marshal(bindy)
                        .convertBodyTo(String.class)
                        .to("mock:marshal");

                from("direct:unmarshal")
                        .unmarshal(bindy)
                        .to("mock:unmarshal");
            }
        };
    }
}
