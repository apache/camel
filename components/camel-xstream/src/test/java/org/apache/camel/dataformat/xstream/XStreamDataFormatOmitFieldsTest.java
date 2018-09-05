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
package org.apache.camel.dataformat.xstream;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * @version 
 */
public class XStreamDataFormatOmitFieldsTest extends CamelTestSupport {

    @Test
    public void testOmitPrice() throws InterruptedException {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        PurchaseOrder purchaseOrder = new PurchaseOrder();
        purchaseOrder.setName("foo");
        purchaseOrder.setPrice(49);
        purchaseOrder.setAmount(3);

        template.sendBody("direct:start", purchaseOrder);

        assertMockEndpointsSatisfied();

        String body = mock.getReceivedExchanges().get(0).getIn().getBody(String.class);        
        assertTrue("Should contain name field", body.contains("<name>"));
        assertFalse("Should not contain price field", body.contains("price"));
        assertTrue("Should contain amount field", body.contains("<amount>"));
    }
    

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                XStreamDataFormat xStreamDataFormat = new XStreamDataFormat();
                Map<String, String[]> omitFields = new HashMap<>();
                omitFields.put(PurchaseOrder.class.getName(), new String[]{"price"});
                xStreamDataFormat.setOmitFields(omitFields);

                from("direct:start").
                        marshal(xStreamDataFormat).
                        convertBodyTo(String.class).
                        to("mock:result");
            }
        };
    }
    

}
