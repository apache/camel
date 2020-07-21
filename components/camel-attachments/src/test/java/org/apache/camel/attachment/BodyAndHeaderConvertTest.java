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
package org.apache.camel.attachment;

import java.net.URL;

import javax.activation.DataHandler;
import javax.activation.URLDataSource;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class BodyAndHeaderConvertTest {
    protected Exchange exchange;

    @Test
    void testConversionOfBody() {
        Document document = exchange.getIn().getBody(Document.class);
        assertNotNull(document);
        Element element = document.getDocumentElement();
        assertEquals("hello", element.getLocalName(), "Root element name");
    }

    @Test
    void testConversionOfExchangeProperties() {
        String text = exchange.getProperty("foo", String.class);
        assertEquals("1234", text, "foo property");
    }

    @Test
    void testConversionOfMessageHeaders() {
        String text = exchange.getIn().getHeader("bar", String.class);
        assertEquals("567", text, "bar header");
    }

    @Test
    void testConversionOfMessageAttachments() {
        DataHandler handler = exchange.getIn(AttachmentMessage.class).getAttachment("att");
        assertNotNull(handler, "attachment got lost");
        Attachment attachment = exchange.getIn(AttachmentMessage.class).getAttachmentObject("att");
        assertNotNull(attachment, "attachment got lost");
    }

    @BeforeEach
    public void setUp() throws Exception {
        exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.setProperty("foo", 1234);
        AttachmentMessage message = exchange.getIn(AttachmentMessage.class);
        message.setBody("<hello>world!</hello>");
        message.setHeader("bar", 567);
        message.addAttachmentObject("att", new DefaultAttachment(new URLDataSource(new URL("http://camel.apache.org/message.html"))));
    }
}
