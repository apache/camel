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

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.jaxb.message.Message;
import org.apache.camel.converter.jaxb.message.ObjectFactory;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class JaxbDataFormatSchemaValidationWithObjectFactoryTest extends CamelTestSupport {

    @EndpointInject("mock:marshall")
    private MockEndpoint mockMarshall;

    @EndpointInject("mock:unmarshall")
    private MockEndpoint mockUnmarshall;

    private JAXBContext jbCtx;
    
    @Override
    public void setUp() throws Exception {
        
        super.setUp();
        
        XmlRootElement xmlRootElementAnnotation = Message.class.getAnnotation(XmlRootElement.class);
        assertNull(xmlRootElementAnnotation);
      
        jbCtx = JAXBContext.newInstance(Message.class);
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
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
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
