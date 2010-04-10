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
package org.apache.camel.dataformat.castor;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * Marhsal tests with domain objects.
 */
public class MarshalDomainObjectTest extends CamelTestSupport {

    @Test
    public void testMarshalDomainObject() throws Exception {
        // some platform cannot test using Castor as it uses a SUN dependent Xerces
        if (isJavaVendor("IBM")) {
            return;
        }

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
        // some platform cannot test using Castor as it uses a SUN dependent Xerces
        if (isJavaVendor("IBM")) {
            return;
        }

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
        assertEquals("The body should marshalled to the same", body1, body2);
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:in").marshal().castor().to("mock:result");
                from("direct:marshal").marshal().castor();
            }
        };
    }

}