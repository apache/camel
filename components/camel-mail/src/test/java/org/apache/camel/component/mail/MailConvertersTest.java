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

import java.io.InputStream;

import jakarta.mail.Message;
import jakarta.mail.Multipart;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mail.Mailbox.MailboxUser;
import org.apache.camel.component.mail.Mailbox.Protocol;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class MailConvertersTest extends CamelTestSupport {

    private MailboxUser james;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        Mailbox.clearAll();
        james = Mailbox.getOrCreateUser("james", "secret");
        super.setUp();
    }

    @Test
    public void testMailMessageToString() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBodyAndHeader("direct:a", "Hello World", "Subject", "Camel rocks");

        MockEndpoint.assertIsSatisfied(context);

        Message mailMessage = mock.getReceivedExchanges().get(0).getIn().getBody(MailMessage.class).getMessage();
        assertNotNull(mailMessage);

        String s = MailConverters.toString(mailMessage);
        assertEquals("Hello World\r\n", s);
    }

    @Test
    public void testMailMessageToInputStream() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBodyAndHeader("direct:a", "Hello World", "Subject", "Camel rocks");

        MockEndpoint.assertIsSatisfied(context);

        Message mailMessage = mock.getReceivedExchanges().get(0).getIn().getBody(MailMessage.class).getMessage();
        assertNotNull(mailMessage);

        InputStream is = MailConverters.toInputStream(mailMessage);
        assertNotNull(is);
        assertEquals("Hello World\r\n", context.getTypeConverter().convertTo(String.class, is));
    }

    @Test
    public void testMultipartToInputStream() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.send("direct:a", new Processor() {
            public void process(Exchange exchange) {
                exchange.getIn().setBody("Hello World");
                exchange.getIn().setHeader(MailConstants.MAIL_ALTERNATIVE_BODY, "Alternative World");
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        Message mailMessage = mock.getReceivedExchanges().get(0).getIn().getBody(MailMessage.class).getMessage();
        assertNotNull(mailMessage);

        Object content = mailMessage.getContent();
        assertIsInstanceOf(Multipart.class, content);

        InputStream is = mock.getReceivedExchanges().get(0).getIn().getBody(InputStream.class);
        assertNotNull(is);
        assertEquals("Alternative World", context.getTypeConverter().convertTo(String.class, is));
    }

    @Test
    public void testMultipartToByteArray() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.send("direct:a", new Processor() {
            public void process(Exchange exchange) {
                exchange.getIn().setBody("Hello World");
                exchange.getIn().setHeader(MailConstants.MAIL_ALTERNATIVE_BODY, "Alternative World");
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        Message mailMessage = mock.getReceivedExchanges().get(0).getIn().getBody(MailMessage.class).getMessage();
        assertNotNull(mailMessage);

        Object content = mailMessage.getContent();
        assertIsInstanceOf(Multipart.class, content);

        byte[] is = mock.getReceivedExchanges().get(0).getIn().getBody(byte[].class);
        assertNotNull(is);
        assertEquals("Alternative World", context.getTypeConverter().convertTo(String.class, is));
    }

    @Test
    public void testMultipartToString() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.send("direct:a", new Processor() {
            public void process(Exchange exchange) {
                exchange.getIn().setBody("Hello World");
                exchange.getIn().setHeader(MailConstants.MAIL_ALTERNATIVE_BODY, "Alternative World");
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        Message mailMessage = mock.getReceivedExchanges().get(0).getIn().getBody(MailMessage.class).getMessage();
        assertNotNull(mailMessage);

        Object content = mailMessage.getContent();
        Multipart mp = assertIsInstanceOf(Multipart.class, content);

        String s = MailConverters.toString(mp);
        assertEquals("Alternative World", s);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:a").to(james.uriPrefix(Protocol.smtp));

                from(james.uriPrefix(Protocol.pop3) + "&initialDelay=100&delay=100&closeFolder=false").to("mock:result");
            }
        };
    }
}
