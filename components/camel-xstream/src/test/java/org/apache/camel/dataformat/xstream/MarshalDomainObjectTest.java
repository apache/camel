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
package org.apache.camel.dataformat.xstream;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Marshal tests with domain objects.
 */
public class MarshalDomainObjectTest extends CamelTestSupport {

    @Test
    public void testMarshalDomainObject() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        PurchaseOrder order = new PurchaseOrder();
        order.setName("Tiger");
        order.setAmount(1);
        order.setPrice(99.95);

        template.sendBody("direct:in", order);

        mock.assertIsSatisfied();
    }

    @Test
    public void testMarshalDomainObjectTwice() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);

        PurchaseOrder order = new PurchaseOrder();
        order.setName("Tiger");
        order.setAmount(1);
        order.setPrice(99.95);

        template.sendBody("direct:in", order);
        template.sendBody("direct:in", order);

        mock.assertIsSatisfied();

        String body1 = mock.getExchanges().get(0).getIn().getBody(String.class);
        String body2 = mock.getExchanges().get(1).getIn().getBody(String.class);
        assertEquals(body1, body2, "The body should marshalled to the same");
    }

    @Test
    public void testMarshalAndUnmarshal() throws Exception {
        PurchaseOrder order = new PurchaseOrder();
        order.setName("Tiger");
        order.setAmount(1);
        order.setPrice(99.95);

        MockEndpoint mock = getMockEndpoint("mock:reverse");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(PurchaseOrder.class);
        mock.message(0).body().isEqualTo(order);

        // we get it back as byte array so type convert it to string
        Object result = template.requestBody("direct:marshal", order);
        String body = context.getTypeConverter().convertTo(String.class, result);        
        template.sendBody("direct:reverse", body);

        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:in").marshal().xstream(PurchaseOrder.class).to("mock:result");

                // just used for helping to marshal
                from("direct:marshal").marshal().xstream("UTF-8", PurchaseOrder.class);

                from("direct:reverse").unmarshal().xstream("UTF-8", PurchaseOrder.class).to("mock:reverse");
            }
        };
    }

}
