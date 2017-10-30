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
import javax.mail.Message;
import javax.mail.Multipart;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.jvnet.mock_javamail.Mailbox;

/**
 * @version 
 */
public class MailConvertersTest extends CamelTestSupport {

    @Override
    public void setUp() throws Exception {
        Mailbox.clearAll();
        super.setUp();
    }

    @Test
    public void testMailMessageToString() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBodyAndHeader("direct:a", "Hello World", "Subject", "Camel rocks");

        assertMockEndpointsSatisfied();

        Message mailMessage = mock.getReceivedExchanges().get(0).getIn().getBody(MailMessage.class).getMessage();
        assertNotNull(mailMessage);

        String s = MailConverters.toString(mailMessage);
        assertEquals("Hello World", s);
    }

    @Test
    public void testMailMessageToInputStream() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBodyAndHeader("direct:a", "Hello World", "Subject", "Camel rocks");

        assertMockEndpointsSatisfied();

        Message mailMessage = mock.getReceivedExchanges().get(0).getIn().getBody(MailMessage.class).getMessage();
        assertNotNull(mailMessage);

        InputStream is = MailConverters.toInputStream(mailMessage);
        assertNotNull(is);
        assertEquals("Hello World", context.getTypeConverter().convertTo(String.class, is));
    }

    @Test
    public void testMultipartToInputStream() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.send("direct:a", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("Hello World");
                exchange.getIn().setHeader(MailConstants.MAIL_ALTERNATIVE_BODY, "Alternative World");
            }
        });

        assertMockEndpointsSatisfied();

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
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("Hello World");
                exchange.getIn().setHeader(MailConstants.MAIL_ALTERNATIVE_BODY, "Alternative World");
            }
        });

        assertMockEndpointsSatisfied();

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
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("Hello World");
                exchange.getIn().setHeader(MailConstants.MAIL_ALTERNATIVE_BODY, "Alternative World");
            }
        });

        assertMockEndpointsSatisfied();

        Message mailMessage = mock.getReceivedExchanges().get(0).getIn().getBody(MailMessage.class).getMessage();
        assertNotNull(mailMessage);

        Object content = mailMessage.getContent();
        Multipart mp = assertIsInstanceOf(Multipart.class, content);

        String s = MailConverters.toString(mp);
        assertEquals("Alternative World", s);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:a").to("smtp://localhost?username=james@localhost");

                from("pop3://localhost?username=james&password=secret&consumer.initialDelay=100&consumer.delay=100").to("mock:result");
            }
        };
    }
}
