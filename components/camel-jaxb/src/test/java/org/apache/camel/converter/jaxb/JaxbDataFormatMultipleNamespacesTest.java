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
package org.apache.camel.converter.jaxb;

import javax.xml.bind.JAXBContext;

import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.example.Address;
import org.apache.camel.example.Order;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class JaxbDataFormatMultipleNamespacesTest extends CamelTestSupport {

    @EndpointInject("mock:marshall")
    private MockEndpoint mockMarshall;

    @EndpointInject("mock:unmarshall")
    private MockEndpoint mockUnmarshall;
    
    @Test
    public void testMarshallMultipleNamespaces() throws Exception {
        mockMarshall.expectedMessageCount(1);

        Order order = new Order();
        order.setId("1");
        Address address = new Address();
        address.setStreet("Main Street");
        address.setStreetNumber("3a");
        address.setZip("65843");
        address.setCity("Sulzbach");
        order.setAddress(address);
        template.sendBody("direct:marshall", order);

        assertMockEndpointsSatisfied();

        String payload = mockMarshall.getExchanges().get(0).getIn().getBody(String.class);
        log.info(payload);

        assertTrue(payload.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"));
        assertTrue(payload.contains("<order:order"));
        assertTrue(payload.contains("<order:id>1</order:id>"));
        assertTrue(payload.contains("<address:address>"));
        assertTrue(payload.contains("<address:street>Main Street</address:street>"));
        assertTrue(payload.contains("<address:streetNumber>3a</address:streetNumber>"));
        assertTrue(payload.contains("<address:zip>65843</address:zip>"));
        assertTrue(payload.contains("<address:city>Sulzbach</address:city>"));
        assertTrue(payload.contains("</address:address>"));
        assertTrue(payload.contains("</order:order>"));

        // the namespaces
        assertTrue(payload.contains("xmlns:address=\"http://www.camel.apache.org/jaxb/example/address/1\""));
        assertTrue(payload.contains("xmlns:order=\"http://www.camel.apache.org/jaxb/example/order/1\""));
    }

    @Test
    public void testUnarshallMultipleNamespaces() throws Exception {
        mockUnmarshall.expectedMessageCount(1);

        String payload = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><ns1:order xmlns:ns2=\"http://www.camel.apache.org/jaxb/example/address/1\""
                        + " xmlns:ns1=\"http://www.camel.apache.org/jaxb/example/order/1\"><ns1:id>1</ns1:id><ns2:address><ns2:street>Main Street</ns2:street>"
                        + "<ns2:streetNumber>3a</ns2:streetNumber><ns2:zip>65843</ns2:zip><ns2:city>Sulzbach</ns2:city></ns2:address></ns1:order>";
        template.sendBody("direct:unmarshall", payload);

        assertMockEndpointsSatisfied();

        Order order = (Order) mockUnmarshall.getExchanges().get(0).getIn().getBody();
        Address address = order.getAddress();
        assertEquals("1", order.getId());
        assertEquals("Main Street", address.getStreet());
        assertEquals("3a", address.getStreetNumber());
        assertEquals("65843", address.getZip());
        assertEquals("Sulzbach", address.getCity());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                JaxbDataFormat jaxbDataFormat = new JaxbDataFormat(JAXBContext.newInstance(Order.class, Address.class));

                from("direct:marshall")
                        .marshal(jaxbDataFormat)
                        .to("mock:marshall");

                from("direct:unmarshall")
                        .unmarshal(jaxbDataFormat)
                        .to("mock:unmarshall");
            }
        };
    }
}
