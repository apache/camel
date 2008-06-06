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

import java.io.StringWriter;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;

import org.apache.camel.ExchangeTestSupport;

/**
 * @version $Revision$
 */
public class MessageTypeTest extends ExchangeTestSupport {
    protected JAXBContext jaxbContext;

    public void testCamelToJaxbUsingExplicitJaxbConverter() throws Exception {
        MessageType messageType = JaxbConverter.toMessageType(exchange);

        assertNotNull("Should have created a valid message Type");

        assertEquals("abc", messageType.getHeaderMap().get("foo"));
        assertEquals(123, messageType.getHeaderMap().get("bar"));
        assertEquals("<hello id='m123'>world!</hello>", messageType.getBody());
        marshalMessage(messageType);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        jaxbContext = JAXBContext.newInstance("org.apache.camel.converter.jaxb");
    }

    protected void marshalMessage(Object object) throws Exception {
        Marshaller marshaller = jaxbContext.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
        StringWriter buffer = new StringWriter();
        marshaller.marshal(object, buffer);
        String out = buffer.toString();
        assertTrue("Should be XML", out.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"));
        assertTrue("Should containt string header", out.indexOf("<header value=\"abc\" name=\"foo\"/>") > -1);
        assertTrue("Should containt int header", out.indexOf("<intHeader value=\"123\" name=\"bar\"/>") > -1);
        assertTrue("Should containt the body", out.indexOf("&lt;hello id='m123'&gt;world!&lt;/hello&gt;") > -1);
    }
}
