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
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class BodyAndHeaderConvertTest extends Assert {
    protected Exchange exchange;

    @Test
    public void testConversionOfBody() throws Exception {
        Document document = exchange.getIn().getBody(Document.class);
        Assert.assertNotNull(document);
        Element element = document.getDocumentElement();
        Assert.assertEquals("Root element name", "hello", element.getLocalName());
    }

    @Test
    public void testConversionOfExchangeProperties() throws Exception {
        String text = exchange.getProperty("foo", String.class);
        Assert.assertEquals("foo property", "1234", text);
    }

    @Test
    public void testConversionOfMessageHeaders() throws Exception {
        String text = exchange.getIn().getHeader("bar", String.class);
        Assert.assertEquals("bar header", "567", text);
    }

    @Test
    public void testConversionOfMessageAttachments() throws Exception {
        DataHandler handler = exchange.getIn(AttachmentMessage.class).getAttachment("att");
        Assert.assertNotNull("attachment got lost", handler);
        Attachment attachment = exchange.getIn(AttachmentMessage.class).getAttachmentObject("att");
        Assert.assertNotNull("attachment got lost", attachment);
    }

    @Before
    public void setUp() throws Exception {
        exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.setProperty("foo", 1234);
        AttachmentMessage message = exchange.getIn(AttachmentMessage.class);
        message.setBody("<hello>world!</hello>");
        message.setHeader("bar", 567);
        message.addAttachmentObject("att", new DefaultAttachment(new URLDataSource(new URL("http://camel.apache.org/message.html"))));
    }
}
