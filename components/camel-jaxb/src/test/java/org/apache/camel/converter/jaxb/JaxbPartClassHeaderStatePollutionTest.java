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

import java.util.Map;

import jakarta.xml.bind.JAXBElement;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.example.Address;
import org.apache.camel.example.PurchaseOrder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that a CamelJaxbPartClass header on one exchange does not permanently mutate the shared JaxbDataFormat
 * instance, which would cause subsequent exchanges (without the header) to unmarshal/marshal using the leaked class.
 *
 * @see <a href="https://issues.apache.org/jira/browse/CAMEL-24164">CAMEL-24164</a>
 */
public class JaxbPartClassHeaderStatePollutionTest extends CamelTestSupport {

    private static final String PURCHASE_ORDER_XML
            = "<purchaseOrder name=\"foo\" price=\"1.23\" amount=\"2.0\"/>";

    private static final String ADDRESS_XML
            = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
              + "<address xmlns=\"http://www.camel.apache.org/jaxb/example/address/1\">"
              + "<street>Main Street</street>"
              + "<streetNumber>3a</streetNumber>"
              + "<zip>65843</zip>"
              + "<city>Sulzbach</city>"
              + "</address>";

    @Test
    void unmarshalPartClassHeaderDoesNotLeakToNextExchange() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:unmarshal");
        mock.expectedMessageCount(2);

        // Exchange 1: unmarshal with CamelJaxbPartClass header -> partial unmarshalling
        template.sendBodyAndHeader("direct:unmarshal", ADDRESS_XML,
                JaxbConstants.JAXB_PART_CLASS, "org.apache.camel.example.Address");

        // Exchange 2: unmarshal WITHOUT header -> should use default (full) unmarshalling
        template.sendBody("direct:unmarshal", PURCHASE_ORDER_XML);

        MockEndpoint.assertIsSatisfied(context);

        Object body1 = mock.getExchanges().get(0).getIn().getBody();
        if (body1 instanceof JAXBElement) {
            body1 = ((JAXBElement<?>) body1).getValue();
        }
        assertInstanceOf(Address.class, body1, "Exchange 1 should unmarshal as Address");

        Object body2 = mock.getExchanges().get(1).getIn().getBody();
        assertInstanceOf(PurchaseOrder.class, body2,
                "Exchange 2 (no header) must NOT leak to Address — should unmarshal as PurchaseOrder");
    }

    @Test
    void marshalPartClassHeaderDoesNotLeakToNextExchange() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:marshal");
        mock.expectedMessageCount(2);

        Address address = new Address();
        address.setStreet("Main Street");
        address.setStreetNumber("3a");
        address.setZip("65843");
        address.setCity("Sulzbach");

        // Exchange 1: marshal with CamelJaxbPartClass + CamelJaxbPartNamespace headers
        template.sendBodyAndHeaders("direct:marshal", address,
                Map.of(
                        JaxbConstants.JAXB_PART_CLASS, "org.apache.camel.example.Address",
                        JaxbConstants.JAXB_PART_NAMESPACE,
                        "{http://www.camel.apache.org/jaxb/example/address/1}address"));

        // Exchange 2: marshal a PurchaseOrder (has @XmlRootElement) WITHOUT headers
        PurchaseOrder order = new PurchaseOrder();
        order.setName("foo");
        order.setPrice(1.23);
        order.setAmount(2.0);
        template.sendBody("direct:marshal", order);

        MockEndpoint.assertIsSatisfied(context);

        String payload1 = mock.getExchanges().get(0).getIn().getBody(String.class);
        String payload2 = mock.getExchanges().get(1).getIn().getBody(String.class);

        assertTrue(payload1.contains("<address:address"),
                "Exchange 1 should use partial marshalling with the header-specified namespace");

        assertFalse(payload2.contains("<address:address"),
                "Exchange 2 (no header) must NOT leak partial marshalling from Exchange 1");
        assertTrue(payload2.contains("<purchaseOrder"),
                "Exchange 2 should marshal as a PurchaseOrder root element");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                JaxbDataFormat jaxb = new JaxbDataFormat();
                jaxb.setContextPath("org.apache.camel.example");

                from("direct:unmarshal")
                        .unmarshal(jaxb)
                        .to("mock:unmarshal");

                JaxbDataFormat jaxbMarshal = new JaxbDataFormat();
                jaxbMarshal.setContextPath("org.apache.camel.example");
                jaxbMarshal.setPrettyPrint(true);

                from("direct:marshal")
                        .marshal(jaxbMarshal)
                        .to("mock:marshal");
            }
        };
    }
}
