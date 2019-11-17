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

import java.io.ByteArrayOutputStream;

import javax.mail.internet.MimeMultipart;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.jvnet.mock_javamail.Mailbox;

import static org.apache.camel.component.mail.MailConstants.MAIL_ALTERNATIVE_BODY;

public class MimeMultipartAlternativeWithContentTypeTest extends CamelTestSupport {
    private String alternativeBody = "hello world! (plain text)";
    private String htmlBody = "<html><body><h1>Hello</h1>World</body></html>";

    private void sendMultipartEmail() throws Exception {
        Mailbox.clearAll();

        // create an exchange with a normal body and attachment to be produced as email
        MailEndpoint endpoint = context.getEndpoint("smtp://sachin@mymailserver.com?password=secret&contentType=text/html; charset=UTF-8", MailEndpoint.class);
        endpoint.getConfiguration().setAlternativeBodyHeader(MailConstants.MAIL_ALTERNATIVE_BODY);

        // create the exchange with the mail message that is multipart with a file and a Hello World text/plain message.
        Exchange exchange = endpoint.createExchange();
        Message in = exchange.getIn();
        in.setBody(htmlBody);
        in.setHeader(MAIL_ALTERNATIVE_BODY, alternativeBody);
        
        // create a producer that can produce the exchange (= send the mail)
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).header(MailConstants.MAIL_ALTERNATIVE_BODY).isNull();

        context.createProducerTemplate().send(endpoint, exchange);
    }

    private void verifyTheRecivedEmail(String expectString) throws Exception {

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.assertIsSatisfied();

        Exchange out = mock.assertExchangeReceived(0);
        ByteArrayOutputStream baos = new ByteArrayOutputStream(((MailMessage)out.getIn()).getMessage().getSize());
        ((MailMessage)out.getIn()).getMessage().writeTo(baos);
        String dumpedMessage = baos.toString();
        assertTrue("There should have the " + expectString, dumpedMessage.indexOf(expectString) > 0);
        log.trace("multipart alternative: \n{}", dumpedMessage);

        // plain text
        assertEquals(alternativeBody, out.getIn().getBody(String.class));

        assertEquals("multipart body should have 2 parts", 2, out.getIn().getBody(MimeMultipart.class).getCount());
    }

    @Test
    public void testMultipartEmailContentType() throws Exception {
        sendMultipartEmail();
        verifyTheRecivedEmail("Content-Type: text/plain; charset=UTF-8");
        verifyTheRecivedEmail("Content-Type: text/html; charset=UTF-8");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("pop3://sachin@mymailserver.com?password=secret&initialDelay=100&delay=100&contentType=text/html; charset=UTF-8").to("mock:result");
            }
        };
    }
}
