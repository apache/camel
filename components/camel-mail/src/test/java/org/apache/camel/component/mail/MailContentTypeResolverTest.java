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

import java.util.Map;
import javax.activation.DataHandler;
import javax.activation.FileDataSource;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.jvnet.mock_javamail.Mailbox;

/**
 * Unit test for Camel attachments and Mail attachments.
 */
public class MailContentTypeResolverTest extends CamelTestSupport {

    @Test
    public void testCustomContentTypeResolver() throws Exception {
        // clear mailbox
        Mailbox.clearAll();

        // create an exchange with a normal body and attachment to be produced as email
        Endpoint endpoint = context.getEndpoint("smtp://james@mymailserver.com?password=secret");

        // create the exchange with the mail message that is multipart with a file and a Hello World text/plain message.
        Exchange exchange = endpoint.createExchange();
        Message in = exchange.getIn();
        in.setBody("Hello World");
        in.addAttachment("logo.jpeg", new DataHandler(new FileDataSource("src/test/data/logo.jpeg")));

        // create a producer that can produce the exchange (= send the mail)
        Producer producer = endpoint.createProducer();
        // start the producer
        producer.start();
        // and let it go (processes the exchange by sending the email)
        producer.process(exchange);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.assertIsSatisfied();
        Exchange out = mock.assertExchangeReceived(0);

        // plain text
        assertEquals("Hello World", out.getIn().getBody(String.class));

        // attachment
        Map<String, DataHandler> attachments = out.getIn().getAttachments();
        assertNotNull("Should have attachments", attachments);
        assertEquals(1, attachments.size());

        DataHandler handler = out.getIn().getAttachment("logo.jpeg");
        assertNotNull("The logo should be there", handler);

        // as we use a custom content type resolver the content type should then be fixed and correct
        // content type should match
        boolean match1 = "image/jpeg; name=logo.jpeg".equals(handler.getContentType());
        boolean match2 = "application/octet-stream; name=logo.jpeg".equals(handler.getContentType());
        assertTrue("Should match 1 or 2", match1 || match2);

        producer.stop();
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                MailComponent mail = getContext().getComponent("smtp", MailComponent.class);
                mail.setContentTypeResolver(new ContentTypeResolver() {
                    public String resolveContentType(String fileName) {
                        return "image/jpeg";
                    }
                });

                from("pop3://james@mymailserver.com?password=secret&consumer.initialDelay=100&consumer.delay=100").to("mock:result");
            }
        };
    }
}