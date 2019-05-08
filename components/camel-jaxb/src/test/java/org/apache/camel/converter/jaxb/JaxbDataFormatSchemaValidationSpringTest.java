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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;

import org.xml.sax.InputSource;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.EndpointInject;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.jaxb.address.Address;
import org.apache.camel.converter.jaxb.message.Message;
import org.apache.camel.converter.jaxb.message.ObjectFactory;
import org.apache.camel.converter.jaxb.person.Person;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class JaxbDataFormatSchemaValidationSpringTest extends CamelSpringTestSupport {

    @EndpointInject("mock:marshall")
    private MockEndpoint mockMarshall;

    @EndpointInject("mock:unmarshall")
    private MockEndpoint mockUnmarshall;

    private JAXBContext jbCtx;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        jbCtx = JAXBContext.newInstance(Person.class, Message.class);
    }

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
        
        Person unmarshalledPerson = (Person) jbCtx.createUnmarshaller().unmarshal(new InputSource(new StringReader(payload)));

        assertNotNull(unmarshalledPerson);
        assertEquals(person.getFirstName(), unmarshalledPerson.getFirstName());
        assertEquals(person.getLastName(), unmarshalledPerson.getLastName());
        assertEquals(person.getAge(), unmarshalledPerson.getAge());
        assertNotNull(unmarshalledPerson.getAddress());
        assertEquals(person.getAddress().getAddressLine1(), unmarshalledPerson.getAddress().getAddressLine1());
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
    
    @Test
    public void testMarshallOfNonRootElementWithValidationException() throws Exception {
        try {
            template.sendBody("direct:marshall", new Message());
            fail("CamelExecutionException expected");
        } catch (CamelExecutionException e) {
            Throwable cause = e.getCause();
            assertIsInstanceOf(IOException.class, cause);
            assertTrue(cause.getMessage().contains("javax.xml.bind.MarshalException"));
            assertTrue(cause.getMessage().contains("org.xml.sax.SAXParseException"));
            assertTrue(cause.getMessage().contains("cvc-complex-type.2.4.b"));
        }
    }
    
    @Test
    public void testUnmarshallOfNonRootWithValidationException() throws Exception {
        JAXBElement<Message> message = new ObjectFactory().createMessage(new Message());
        
        String xml;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            jbCtx.createMarshaller().marshal(message, baos);
            xml = new String(baos.toByteArray(), "UTF-8");
        }
        
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
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/converter/jaxb/context.xml");
    }
}