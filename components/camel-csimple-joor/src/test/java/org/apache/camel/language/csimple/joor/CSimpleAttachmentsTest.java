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
package org.apache.camel.language.csimple.joor;

import java.io.File;

import jakarta.activation.DataHandler;

import org.apache.camel.Exchange;
import org.apache.camel.attachment.AttachmentMessage;
import org.apache.camel.attachment.CamelFileDataSource;
import org.apache.camel.attachment.DefaultAttachment;
import org.apache.camel.test.junit5.LanguageTestSupport;
import org.junit.jupiter.api.Test;

public class CSimpleAttachmentsTest extends LanguageTestSupport {

    @Override
    protected String getLanguageName() {
        return "csimple";
    }

    @Override
    protected void populateExchange(Exchange exchange) {
        super.populateExchange(exchange);

        DefaultAttachment da
                = new DefaultAttachment(new DataHandler(new CamelFileDataSource(new File("src/test/data/message1.xml"))));
        da.addHeader("orderId", "123");
        exchange.getIn(AttachmentMessage.class).addAttachmentObject("message1.xml", da);

        da = new DefaultAttachment(new DataHandler(new CamelFileDataSource(new File("src/test/data/message2.xml"))));
        da.addHeader("orderId", "456");
        exchange.getIn(AttachmentMessage.class).addAttachmentObject("message2.xml", da);

        da = new DefaultAttachment(new DataHandler(new CamelFileDataSource(new File("src/test/data/123.txt"))));
        da.addHeader("orderId", "0");
        exchange.getIn(AttachmentMessage.class).addAttachmentObject("123.txt", da);
    }

    @Test
    public void testAttachments() throws Exception {
        var map = exchange.getMessage(AttachmentMessage.class).getAttachments();
        assertExpression("${attachments}", map);
        assertExpression("${attachments.size}", 3);
        assertExpression("${attachments.length}", 3);

        exchange.getMessage(AttachmentMessage.class).removeAttachment("message1.xml");
        assertExpression("${attachments.size}", 2);
        assertExpression("${attachments.length}", 2);

        exchange.getMessage(AttachmentMessage.class).removeAttachment("message2.xml");
        assertExpression("${attachments.size}", 1);
        assertExpression("${attachments.length}", 1);

        exchange.getMessage(AttachmentMessage.class).removeAttachment("123.txt");
        assertExpression("${attachments.size}", 0);
        assertExpression("${attachments.length}", 0);
    }

    @Test
    public void testAttachmentContent() throws Exception {
        var map = exchange.getMessage(AttachmentMessage.class).getAttachments();
        Object is1 = map.get("message1.xml").getContent();
        String xml1 = context.getTypeConverter().convertTo(String.class, is1);
        Object is2 = map.get("message2.xml").getContent();
        String xml2 = context.getTypeConverter().convertTo(String.class, is2);

        assertExpression("${attachmentContent(message1.xml)}", xml1);
        assertExpression("${attachmentContentAs(message2.xml,String)}", xml2);
        assertExpression("${attachmentContentAsText(message2.xml)}", xml2);
        assertExpression("${attachmentContent(123.txt)}", "456");
        assertExpression("${attachmentContentAs(123.txt,String)}", "456");
        assertExpression("${attachmentContentAsText(123.txt)}", "456");
    }

    @Test
    public void testAttachmentContentHeader() throws Exception {
        assertExpression("${attachmentHeader(message1.xml,orderId)}", "123");
        assertExpression("${attachmentHeader(message1.xml,foo)}", null);
        assertExpression("${attachmentHeaderAs(message2.xml,orderId,int)}", 456);
        assertExpression("${attachmentHeader(123.txt,orderId)}", 0);
        assertExpression("${attachmentHeader(123.txt,unknown)}", null);
    }

    @Test
    public void testAttachmentContentType() throws Exception {
        assertExpression("${attachmentContentType(message1.xml)}", "application/octet-stream");
        assertExpression("${attachmentContentType(message2.xml)}", "application/octet-stream");
        assertExpression("${attachmentContentType(123.txt)}", "text/plain");
    }

}
