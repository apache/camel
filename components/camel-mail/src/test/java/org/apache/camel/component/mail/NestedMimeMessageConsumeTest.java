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
package org.apache.camel.component.mail;

import java.io.InputStream;
import java.util.Set;

import javax.activation.DataHandler;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Store;
import javax.mail.internet.MimeMessage;

import org.apache.camel.Attachment;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.jvnet.mock_javamail.Mailbox;

import static org.hamcrest.core.AnyOf.anyOf;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.StringContains.containsString;

public class NestedMimeMessageConsumeTest extends CamelTestSupport {

    @Test
    public void testNestedMultipart() throws Exception {
        Mailbox.clearAll();

        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        resultEndpoint.expectedMinimumMessageCount(1);

        prepareMailbox("james3");

        resultEndpoint.assertIsSatisfied();

        Exchange exchange = resultEndpoint.getReceivedExchanges().get(0);
        String text = exchange.getIn().getBody(String.class);
        assertThat(text, containsString("Test with bold face, pictures and attachments"));
        assertEquals("text/plain; charset=us-ascii", exchange.getIn().getHeader("Content-Type"));

        Set<String> attachmentNames = exchange.getIn().getAttachmentNames();
        assertNotNull("attachments got lost", attachmentNames);
        assertEquals(2, attachmentNames.size());
        for (String s : attachmentNames) {
            Attachment att = exchange.getIn().getAttachmentObject(s);
            DataHandler dh = att.getDataHandler();
            Object content = dh.getContent();
            assertNotNull("Content should not be empty", content);
            assertThat(dh.getName(), anyOf(equalTo("image001.png"), equalTo("test.txt")));
        }
    }

    private void prepareMailbox(String user) throws MessagingException {
        // connect to mailbox
        JavaMailSender sender = new DefaultJavaMailSender();
        Store store = sender.getSession().getStore("pop3");
        store.connect("localhost", 25, user, "secret");
        Folder folder = store.getFolder("INBOX");
        folder.open(Folder.READ_WRITE);
        folder.expunge();

        InputStream is = getClass().getResourceAsStream("/nested-multipart.elm");
        Message hurzMsg = new MimeMessage(sender.getSession(), is);
        Message[] messages = new Message[] {hurzMsg};

        // insert one signed message
        folder.appendMessages(messages);
        folder.close(true);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("pop3://james3@localhost?consumer.initialDelay=100&consumer.delay=100").removeHeader("to").to("smtp://james4@localhost");
                from("pop3://james4@localhost?consumer.initialDelay=200&consumer.delay=100").convertBodyTo(String.class).to("mock:result");
            }
        };
    }
}
