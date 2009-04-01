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

import java.io.ByteArrayOutputStream;
import java.util.Map;

import javax.activation.DataHandler;
import javax.activation.FileDataSource;
import javax.mail.internet.MimeMultipart;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;


public class MimeMultipartAlternativeTest extends ContextTestSupport {
    private String alternativeBody = "hello world! (plain text)";
    private String htmlBody = "<html><body><h1>Hello</h1>World<img src=\"cid:0001\"></body></html>";

    private void sendMultipartEmail(boolean useInlineattachments) throws Exception {
     // create an exchange with a normal body and attachment to be produced as email
        MailEndpoint endpoint = context.getEndpoint("smtp://ryan@mymailserver.com?password=secret", MailEndpoint.class);
        endpoint.getConfiguration().setUseInlineAttachments(useInlineattachments);
        endpoint.getConfiguration().setAlternateBodyHeader(MailConfiguration.DEFAULT_ALTERNATE_BODY_HEADER);

        // create the exchange with the mail message that is multipart with a file and a Hello World text/plain message.
        Exchange exchange = endpoint.createExchange();
        Message in = exchange.getIn();
        in.setBody(htmlBody);
        in.setHeader("mail_alternateBody", alternativeBody);
        in.addAttachment("cid:0001", new DataHandler(new FileDataSource("src/test/data/logo.jpeg")));

        // create a producer that can produce the exchange (= send the mail)
        Producer producer = endpoint.createProducer();
        // start the producer
        producer.start();
        // and let it go (processes the exchange by sending the email)
        producer.process(exchange); 
        
        producer.stop();

    }
    
    private void verifyTheRecivedEmail(String expectString) throws Exception {
        // need some time for the mail to arrive on the inbox (consumed and sent to the mock)
        Thread.sleep(1000);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        Exchange out = mock.assertExchangeReceived(0);
        mock.assertIsSatisfied();

        ByteArrayOutputStream baos = new ByteArrayOutputStream(((MailMessage)out.getIn()).getMessage().getSize());
        ((MailMessage)out.getIn()).getMessage().writeTo(baos);
        String dumpedMessage = baos.toString();
        assertTrue("There should have the " + expectString, dumpedMessage.indexOf(expectString) > 0);
        if (log.isTraceEnabled()) {
            log.trace("multipart alternative: \n" + dumpedMessage);
        }
         
        // plain text
        assertEquals(alternativeBody, out.getIn().getBody(String.class));

        // attachment
        Map<String, DataHandler> attachments = out.getIn().getAttachments();
        assertNotNull("Should not have null attachments", attachments);
        assertEquals(1, attachments.size());
        assertEquals("multipart body should have 2 parts", 2, out.getIn().getBody(MimeMultipart.class).getCount());

        
    }
    public void testMultipartEmailWithInlineAttachments() throws Exception {
        sendMultipartEmail(true);
        verifyTheRecivedEmail("Content-Disposition: inline; filename=\"cid:0001\"");
    }    
        
    public void testMultipartEmailWithRegularAttachments() throws Exception {
        sendMultipartEmail(false);
        verifyTheRecivedEmail("Content-Disposition: attachment; filename=\"cid:0001\"");
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("pop3://ryan@mymailserver.com?password=secret&consumer.delay=1000").to("mock:result");
            }
        };
    }
}
