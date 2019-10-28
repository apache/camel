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
package org.apache.camel.component.mail;

import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.Map;

import javax.activation.DataHandler;
import javax.activation.FileDataSource;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.attachment.AttachmentMessage;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Ignore;
import org.junit.Test;
import org.jvnet.mock_javamail.Mailbox;

/**
 * Unit test for Camel attachments and Mail attachments.
 */
@Ignore("Fails on CI servers and some platforms - maybe due locale or something")
public class MailAttachmentsUmlautIssueTest extends CamelTestSupport {

    @Test
    public void testSendAndReceiveMailWithAttachments() throws Exception {
        // clear mailbox
        Mailbox.clearAll();

        // create an exchange with a normal body and attachment to be produced as email
        Endpoint endpoint = context.getEndpoint("smtp://james@mymailserver.com?password=secret");

        // create the exchange with the mail message that is multipart with a file and a Hello World text/plain message.
        Exchange exchange = endpoint.createExchange();
        AttachmentMessage in = exchange.getIn(AttachmentMessage.class);
        in.setBody("Hello World");
        // unicode 00DC is german umlaut
        String name = "logo2\u00DC";
        // use existing logo.jpeg file, but lets name it with the umlaut
        in.addAttachment(name, new DataHandler(new FileDataSource("src/test/data/logo.jpeg")));

        // create a producer that can produce the exchange (= send the mail)
        Producer producer = endpoint.createProducer();
        // start the producer
        producer.start();
        // and let it go (processes the exchange by sending the email)
        producer.process(exchange);

        // need some time for the mail to arrive on the inbox (consumed and sent to the mock)
        Thread.sleep(2000);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        Exchange out = mock.assertExchangeReceived(0);
        mock.assertIsSatisfied();

        // plain text
        assertEquals("Hello World", out.getIn().getBody(String.class));

        // attachment
        Map<String, DataHandler> attachments = out.getIn(AttachmentMessage.class).getAttachments();
        assertNotNull("Should have attachments", attachments);
        assertEquals(1, attachments.size());

        DataHandler handler = out.getIn(AttachmentMessage.class).getAttachment(name);
        assertNotNull("The " + name + " should be there", handler);

        String nameURLEncoded = URLEncoder.encode(name, Charset.defaultCharset().name());
        assertTrue("Handler content type should end with URL-encoded name", handler.getContentType().endsWith(nameURLEncoded));

        assertEquals("Handler name should be the file name", name, handler.getName());

        producer.stop();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("pop3://james@mymailserver.com?password=secret&initialDelay=100&delay=100").to("mock:result");
            }
        };
    }
}
