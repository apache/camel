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

import javax.mail.Address;
import javax.mail.Message.RecipientType;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.jvnet.mock_javamail.Mailbox;

public class MailProducerTest extends CamelTestSupport {

    @Test
    public void testProduer() throws Exception {
        Mailbox.clearAll();
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBodyAndHeader("direct:start", "Message ", "To", "someone@localhost");
        assertMockEndpointsSatisfied();
        // need to check the message header
        Exchange exchange = getMockEndpoint("mock:result").getExchanges().get(0);
        assertNotNull("The message id should not be null", exchange.getIn().getHeader(MailConstants.MAIL_MESSAGE_ID));

        Mailbox box = Mailbox.get("someone@localhost");
        assertEquals(1, box.size());
    }
    
    @Test
    public void testProducerBodyIsMimeMessage() throws Exception {
        Mailbox.clearAll();
        getMockEndpoint("mock:result").expectedMessageCount(1);

        Address from = new InternetAddress("fromCamelTest@localhost");
        Address to = new InternetAddress("recipient2@localhost");
        Session session = Session.getDefaultInstance(System.getProperties());
        MimeMessage mimeMessage = new MimeMessage(session);
        mimeMessage.setFrom(from);
        mimeMessage.addRecipient(RecipientType.TO, to);
        mimeMessage.setSubject("This is the subject.");
        mimeMessage.setText("This is the message");        
        
        template.sendBodyAndHeader("direct:start", mimeMessage, "To", "someone@localhost");
        assertMockEndpointsSatisfied();
        // need to check the message header
        Exchange exchange = getMockEndpoint("mock:result").getExchanges().get(0);
        assertNotNull("The message id should not be null", exchange.getIn().getHeader(MailConstants.MAIL_MESSAGE_ID));

        Mailbox box = Mailbox.get("someone@localhost");
        assertEquals(0, box.size());
        
        // Check if the mimeMessagea has override body and headers
        Mailbox box2 = Mailbox.get("recipient2@localhost");
        assertEquals(1, box2.size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("smtp://camel@localhost", "mock:result");
            }
        };
    }

}
