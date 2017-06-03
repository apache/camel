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
package org.apache.camel;

import java.net.URL;

import javax.activation.DataHandler;
import javax.activation.URLDataSource;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import junit.framework.TestCase;

import org.apache.camel.impl.DefaultAttachment;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;

/**
 * @version 
 */
public class BodyAndHeaderConvertTest extends TestCase {
    protected Exchange exchange;

    public void testConversionOfBody() throws Exception {
        Document document = exchange.getIn().getBody(Document.class);
        assertNotNull(document);
        Element element = document.getDocumentElement();
        assertEquals("Root element name", "hello", element.getLocalName());
    }

    public void testConversionOfExchangeProperties() throws Exception {
        String text = exchange.getProperty("foo", String.class);
        assertEquals("foo property", "1234", text);
    }

    public void testConversionOfMessageHeaders() throws Exception {
        String text = exchange.getIn().getHeader("bar", String.class);
        assertEquals("bar header", "567", text);
    }

    public void testConversionOfMessageAttachments() throws Exception {
        DataHandler handler = exchange.getIn().getAttachment("att");
        assertNotNull("attachment got lost", handler);
        Attachment attachment = exchange.getIn().getAttachmentObject("att");
        assertNotNull("attachment got lost", attachment);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.setProperty("foo", 1234);
        Message message = exchange.getIn();
        message.setBody("<hello>world!</hello>");
        message.setHeader("bar", 567);
        message.addAttachmentObject("att", new DefaultAttachment(new URLDataSource(new URL("http://camel.apache.org/message.html"))));
    }
}
