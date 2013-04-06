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
package org.apache.camel.converter.jaxb;

import javax.xml.namespace.QName;

import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.example.Address;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class JaxbDataFormatPartClassTest extends CamelTestSupport {

    @EndpointInject(uri = "mock:marshall")
    private MockEndpoint mockMarshall;

    @Test
    public void testMarshallMultipleNamespaces() throws Exception {
        mockMarshall.expectedMessageCount(1);

        Address address = new Address();
        address.setStreet("Main Street");
        address.setStreetNumber("3a");
        address.setZip("65843");
        address.setCity("Sulzbach");
        template.sendBody("direct:marshall", address);

        assertMockEndpointsSatisfied();

        String payload = mockMarshall.getExchanges().get(0).getIn().getBody(String.class);
        log.info(payload);

        assertTrue(payload.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"));
        assertTrue(payload.contains("<address:address"));
        assertTrue(payload.contains("<address:street>Main Street</address:street>"));
        assertTrue(payload.contains("<address:streetNumber>3a</address:streetNumber>"));
        assertTrue(payload.contains("<address:zip>65843</address:zip>"));
        assertTrue(payload.contains("<address:city>Sulzbach</address:city>"));
        assertTrue(payload.contains("</address:address>"));

        // the namespaces
        assertTrue(payload.contains("xmlns:address=\"http://www.camel.apache.org/jaxb/example/address/1\""));
        assertTrue(payload.contains("xmlns:order=\"http://www.camel.apache.org/jaxb/example/order/1\""));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                JaxbDataFormat jaxbDataFormat = new JaxbDataFormat();
                jaxbDataFormat.setContextPath(Address.class.getPackage().getName());
                jaxbDataFormat.setPartClass("org.apache.camel.example.Address");
                jaxbDataFormat.setPartNamespace(new QName("http://www.camel.apache.org/jaxb/example/address/1", "address"));
                jaxbDataFormat.setPrettyPrint(true);

                from("direct:marshall")
                        .marshal(jaxbDataFormat)
                        .to("mock:marshall");
            }
        };
    }
}
