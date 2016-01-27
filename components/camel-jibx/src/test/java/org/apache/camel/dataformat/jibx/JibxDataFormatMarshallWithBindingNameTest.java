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
package org.apache.camel.dataformat.jibx;

import java.io.IOException;
import java.io.StringReader;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Element;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dataformat.jibx.model.PurchaseOrder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;


/**
 * @version
 */
public class JibxDataFormatMarshallWithBindingNameTest extends CamelTestSupport {
    private static final String BINDING_NAME = "purchaseOrder-jibx";

    @Test
    public void testMarshall() throws InterruptedException, ParserConfigurationException, IOException, SAXException {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        PurchaseOrder purchaseOrder = new PurchaseOrder();
        String name = "foo";
        purchaseOrder.setName(name);
        double price = 49;
        purchaseOrder.setPrice(price);
        double amount = 3;
        purchaseOrder.setAmount(amount);

        template.sendBody("direct:start", purchaseOrder);

        assertMockEndpointsSatisfied();

        String body = mock.getReceivedExchanges().get(0).getIn().getBody(String.class);
        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Element root = builder.parse(new InputSource(new StringReader(body))).getDocumentElement();

        assertEquals(name, root.getAttribute("name"));
        assertEquals(price + "", root.getAttribute("price"));
        assertEquals(amount + "", root.getAttribute("amount"));
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                JibxDataFormat jibxDataFormat = new JibxDataFormat(PurchaseOrder.class, BINDING_NAME);

                from("direct:start").marshal(jibxDataFormat).convertBodyTo(String.class).to("mock:result");
            }
        };
    }

}
