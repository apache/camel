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

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.annotation.XmlRootElement;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.jaxb.message.Message;
import org.apache.camel.converter.jaxb.message.ObjectFactory;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JaxbDataFormatSchemaValidationWithObjectFactoryTest extends CamelTestSupport {

    @EndpointInject("mock:marshall")
    private MockEndpoint mockMarshall;

    @EndpointInject("mock:unmarshall")
    private MockEndpoint mockUnmarshall;

    private JAXBContext jbCtx;

    @Override
    @BeforeEach
    public void setUp() throws Exception {

        super.setUp();

        XmlRootElement xmlRootElementAnnotation = Message.class.getAnnotation(XmlRootElement.class);
        assertNull(xmlRootElementAnnotation);

        jbCtx = JAXBContext.newInstance(Message.class);
    }

    @Test
    public void testMarshallOfNonRootElementWithValidationException() {
        Message message = new Message();
        Exception ex = Assertions.assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:marshall", message));

        Throwable cause = ex.getCause();
        assertIsInstanceOf(IOException.class, cause);
        assertTrue(cause.getMessage().contains("jakarta.xml.bind.MarshalException"));
        assertTrue(cause.getMessage().contains("org.xml.sax.SAXParseException"));
        assertTrue(cause.getMessage().contains("cvc-complex-type.2.4.b"));
    }

    @Test
    public void testUnmarshallOfNonRootWithValidationException() throws Exception {
        JAXBElement<Message> message = new ObjectFactory().createMessage(new Message());

        String xml;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            jbCtx.createMarshaller().marshal(message, baos);
            xml = new String(baos.toByteArray(), "UTF-8");
        }

        Exception ex = Assertions.assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:unmarshall", xml));

        Throwable cause = ex.getCause();
        assertIsInstanceOf(IOException.class, cause);
        assertTrue(cause.getMessage().contains("jakarta.xml.bind.UnmarshalException"));
        assertTrue(cause.getMessage().contains("org.xml.sax.SAXParseException"));
        assertTrue(cause.getMessage().contains("cvc-complex-type.2.4.b"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                JaxbDataFormat jaxbDataFormat = new JaxbDataFormat();
                jaxbDataFormat.setContextPath(Message.class.getPackage().getName());
                jaxbDataFormat.setSchema("classpath:message.xsd");
                // if the following is removed the lookup of an object factory method which can create the element
                // won't be done and the object won'T get marshalled
                jaxbDataFormat.setObjectFactory(true);

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
