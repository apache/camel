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

import java.io.IOException;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.jaxb.address.Address;
import org.apache.camel.converter.jaxb.person.Person;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class JaxbDataFormatSchemaValidationTest extends CamelTestSupport {

    @EndpointInject("mock:marshall")
    private MockEndpoint mockMarshall;

    @EndpointInject("mock:unmarshall")
    private MockEndpoint mockUnmarshall;

    @Test
    public void testMarshallSuccess() throws Exception {
        mockMarshall.expectedMessageCount(1);

        Address address = new Address();
        address.setAddressLine1("Hauptstr. 1; 01129 Entenhausen");
        Person person = new Person();
        person.setFirstName("Christian");
        person.setLastName("Mueller");
        person.setAge(Integer.valueOf(36));
        person.setAddress(address);

        template.sendBody("direct:marshall", person);

        assertMockEndpointsSatisfied();

        String payload = mockMarshall.getExchanges().get(0).getIn().getBody(String.class);
        log.info(payload);

        assertTrue(payload.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"));
        assertTrue(payload.contains("<person xmlns=\"person.jaxb.converter.camel.apache.org\" xmlns:ns2=\"address.jaxb.converter.camel.apache.org\">"));
        assertTrue(payload.contains("<firstName>Christian</firstName>"));
        assertTrue(payload.contains("<lastName>Mueller</lastName>"));
        assertTrue(payload.contains("<age>36</age>"));
        assertTrue(payload.contains("<address>"));
        assertTrue(payload.contains("<ns2:addressLine1>Hauptstr. 1; 01129 Entenhausen</ns2:addressLine1>"));
        assertTrue(payload.contains("</address>"));
        assertTrue(payload.contains("</person>"));
    }

    @Test
    public void testMarshallWithValidationException() throws Exception {
        try {
            template.sendBody("direct:marshall", new Person());
            fail("CamelExecutionException expected");
        } catch (CamelExecutionException e) {
            Throwable cause = e.getCause();
            assertIsInstanceOf(IOException.class, cause);
            assertTrue(cause.getMessage().contains("javax.xml.bind.MarshalException"));
            assertTrue(cause.getMessage().contains("org.xml.sax.SAXParseException"));
            assertTrue(cause.getMessage().contains("cvc-complex-type.2.4.a"));
        }
    }

    @Test
    public void testUnmarshallSuccess() throws Exception {
        mockUnmarshall.expectedMessageCount(1);

        String xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
            .append("<person xmlns=\"person.jaxb.converter.camel.apache.org\" xmlns:ns2=\"address.jaxb.converter.camel.apache.org\">")
            .append("<firstName>Christian</firstName>")
            .append("<lastName>Mueller</lastName>")
            .append("<age>36</age>")
            .append("<address>")
            .append("<ns2:addressLine1>Hauptstr. 1; 01129 Entenhausen</ns2:addressLine1>")
            .append("</address>")
            .append("</person>")
            .toString();
        template.sendBody("direct:unmarshall", xml);

        assertMockEndpointsSatisfied();

        Person person = mockUnmarshall.getExchanges().get(0).getIn().getBody(Person.class);

        assertEquals("Christian", person.getFirstName());
        assertEquals("Mueller", person.getLastName());
        assertEquals(Integer.valueOf(36), person.getAge());
    }

    @Test
    public void testUnmarshallWithValidationException() throws Exception {
        String xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
            .append("<person xmlns=\"person.jaxb.converter.camel.apache.org\" />")
            .toString();
        
        try {
            template.sendBody("direct:unmarshall", xml);
            fail("CamelExecutionException expected");
        } catch (CamelExecutionException e) {
            Throwable cause = e.getCause();
            assertIsInstanceOf(IOException.class, cause);
            assertTrue(cause.getMessage().contains("javax.xml.bind.UnmarshalException"));
            assertTrue(cause.getMessage().contains("org.xml.sax.SAXParseException"));
            assertTrue(cause.getMessage().contains("cvc-complex-type.2.4.b"));
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                JaxbDataFormat jaxbDataFormat = new JaxbDataFormat();
                jaxbDataFormat.setContextPath(Person.class.getPackage().getName());
                jaxbDataFormat.setSchema("classpath:person.xsd,classpath:address.xsd");

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