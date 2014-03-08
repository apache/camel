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
package org.apache.camel.converter.xmlbeans;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import samples.services.xsd.BuyStocksDocument;
import samples.services.xsd.BuyStocksDocument.BuyStocks;
import samples.services.xsd.Order;

public class XmlBeansDslTest extends CamelTestSupport {

    @Test
    public void testSendXmlAndUnmarshal() throws Exception {
        MockEndpoint unmarshal = getMockEndpoint("mock:unmarshal");
        unmarshal.expectedMessageCount(1);

        MockEndpoint marshal = getMockEndpoint("mock:marshal");
        marshal.expectedMessageCount(1);

        template.sendBody("direct:start", createBuyStocksDocument());

        assertMockEndpointsSatisfied();
        
        Object marshaledBody = marshal.getReceivedExchanges().get(0).getIn().getBody();
        assertIsInstanceOf(byte[].class, marshaledBody);

        Object unmarshaledBody = unmarshal.getReceivedExchanges().get(0).getIn().getBody();
        assertIsInstanceOf(BuyStocksDocument.class, unmarshaledBody);
    }

    private BuyStocksDocument createBuyStocksDocument() {
        BuyStocksDocument document = BuyStocksDocument.Factory.newInstance();
        BuyStocks payload = document.addNewBuyStocks();
        Order order = payload.addNewOrder();
        order.setSymbol("IBM");
        order.setBuyerID("cmueller");
        order.setPrice(140.34);
        order.setVolume(2000);

        return document;
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                    .marshal().xmlBeans()
                    .to("mock:marshal")
                    .unmarshal().xmlBeans()
                    .to("mock:unmarshal");
            }
        };
    }
}