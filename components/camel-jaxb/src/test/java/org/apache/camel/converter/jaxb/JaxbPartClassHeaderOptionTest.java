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

import javax.xml.namespace.QName;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.example.Address;
import org.apache.camel.example.Partial;
import org.apache.camel.example.PurchaseOrder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The CamelJaxbPartClass and CamelJaxbPartNamespace headers are used only when allowPartClassFromHeader is enabled.
 */
public class JaxbPartClassHeaderOptionTest extends CamelTestSupport {

    private static final String ADDRESS_NAMESPACE = "http://www.camel.apache.org/jaxb/example/address/1";

    private static final String PARTIAL_XML = "<partial name=\"foo\"><location>Here</location></partial>";

    private static final String PURCHASE_ORDER_XML = "<purchaseOrder name=\"foo\" price=\"1.23\" amount=\"2.0\"/>";

    @Test
    public void partClassHeaderIgnoredByDefault() {
        Object body = template.requestBodyAndHeader("direct:unmarshal", PARTIAL_XML,
                JaxbConstants.JAXB_PART_CLASS, Address.class.getName());

        Partial partial = assertInstanceOf(Partial.class, body);
        assertEquals("foo", partial.getName());
        assertEquals("Here", partial.getLocation());
    }

    @Test
    public void partClassHeaderUsedWhenAllowed() {
        Object body = template.requestBodyAndHeader("direct:unmarshalAllowed", PARTIAL_XML,
                JaxbConstants.JAXB_PART_CLASS, Address.class.getName());

        assertInstanceOf(Address.class, body);
    }

    @Test
    public void partClassHeaderAloneDoesNotEnablePartialUnmarshalByDefault() {
        Object body = template.requestBodyAndHeader("direct:unmarshalNoPartClass", PURCHASE_ORDER_XML,
                JaxbConstants.JAXB_PART_CLASS, Address.class.getName());

        PurchaseOrder order = assertInstanceOf(PurchaseOrder.class, body);
        assertEquals("foo", order.getName());
    }

    @Test
    public void partNamespaceHeaderIgnoredByDefaultOnMarshal() {
        String xml = template.requestBodyAndHeader("direct:marshal", createAddress(),
                JaxbConstants.JAXB_PART_NAMESPACE, "{" + ADDRESS_NAMESPACE + "}shippingAddress", String.class);

        assertTrue(xml.contains("<address:address"), xml);
        assertFalse(xml.contains("shippingAddress"), xml);
    }

    @Test
    public void partNamespaceHeaderUsedWhenAllowed() {
        String xml = template.requestBodyAndHeader("direct:marshalAllowed", createAddress(),
                JaxbConstants.JAXB_PART_NAMESPACE, "{" + ADDRESS_NAMESPACE + "}shippingAddress", String.class);

        assertTrue(xml.contains("<address:shippingAddress"), xml);
    }

    private static Address createAddress() {
        Address address = new Address();
        address.setStreet("Main Street");
        address.setStreetNumber("3a");
        address.setZip("65843");
        address.setCity("Sulzbach");
        return address;
    }

    private static JaxbDataFormat createDataFormat(Class<?> partClass, boolean allowPartClassFromHeader) {
        JaxbDataFormat jaxb = new JaxbDataFormat();
        jaxb.setContextPath(Address.class.getPackage().getName());
        jaxb.setPartClass(partClass);
        jaxb.setAllowPartClassFromHeader(allowPartClassFromHeader);
        return jaxb;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:unmarshal").unmarshal(createDataFormat(Partial.class, false));
                from("direct:unmarshalAllowed").unmarshal(createDataFormat(Partial.class, true));
                from("direct:unmarshalNoPartClass").unmarshal(createDataFormat(null, false));

                JaxbDataFormat marshal = createDataFormat(Address.class, false);
                marshal.setPartNamespace(new QName(ADDRESS_NAMESPACE, "address"));
                from("direct:marshal").marshal(marshal);

                JaxbDataFormat marshalAllowed = createDataFormat(Address.class, true);
                marshalAllowed.setPartNamespace(new QName(ADDRESS_NAMESPACE, "address"));
                from("direct:marshalAllowed").marshal(marshalAllowed);
            }
        };
    }
}
