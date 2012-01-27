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
package org.apache.camel.dataformat.bindy.csv;

import java.util.List;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dataformat.bindy.model.tab.PurchaseOrder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.CastUtils;

import org.junit.Test;

/**
 * @version 
 */
public class BindyTabSeparatorTest extends CamelTestSupport {

    @Test
    public void testUnmarshal() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:unmarshal");
        mock.expectedMessageCount(1);

        template.sendBody("direct:unmarshal", "123\tCamel in Action\t2");

        assertMockEndpointsSatisfied();

        List<Map<?, PurchaseOrder>> rows = CastUtils.cast(mock.getReceivedExchanges().get(0).getIn().getBody(List.class));
        PurchaseOrder order = rows.get(0).get(PurchaseOrder.class.getName());

        assertEquals(123, order.getId());
        assertEquals("Camel in Action", order.getName());
        assertEquals(2, order.getAmount());
    }

    @Test
    public void testMarshal() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:marshal");
        mock.expectedBodiesReceived("123\tCamel in Action\t2\n");

        PurchaseOrder order = new PurchaseOrder();
        order.setId(123);
        order.setName("Camel in Action");
        order.setAmount(2);

        template.sendBody("direct:marshal", order);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                BindyCsvDataFormat bindy = new BindyCsvDataFormat("org.apache.camel.dataformat.bindy.model.tab");

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
