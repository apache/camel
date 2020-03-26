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

import java.util.Properties;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.jvnet.mock_javamail.Mailbox;

public class AuthenticatorTest extends CamelTestSupport {

    /**
     * Checks that the authenticator does dynamically return passwords for the
     * smtp endpoint.
     */
    @Test
    public void testSendAndReceiveMails() throws Exception {
        Mailbox.clearAll();
        // first expect correct result because smtp authenticator does not return wrong password       
        callAndCheck("mock:result");
        // second expect exception  because smtp authenticator does return wrong password       
        callAndCheck("mock:exception");
        // third expect correct result because smtp authenticator does not return wrong password       
        callAndCheck("mock:result");
    }

    private String callAndCheck(String endpoint) throws MessagingException, InterruptedException {
        MockEndpoint resultEndpoint = getMockEndpoint(endpoint);
        resultEndpoint.expectedMinimumMessageCount(1);
        //resultEndpoint.setResultWaitTime(60000);
        String body = "hello world!";
        execute("james3@localhost", body);

        resultEndpoint.assertIsSatisfied();

        Exchange exchange = resultEndpoint.getReceivedExchanges().get(0);
        String text = exchange.getIn().getBody(String.class);
        assertEquals("mail body", body, text);
        return body;
    }

    private void execute(String mailAddress, String body) throws MessagingException, InterruptedException {

        Properties properties = new Properties();
        properties.put("mail.smtp.host", "localhost");
        Session session = Session.getInstance(properties, null);

        MimeMessage message = new MimeMessage(session);
        populateMimeMessageBody(message, body);
        message.setRecipients(Message.RecipientType.TO, mailAddress);

        Transport.send(message);

    }

    protected void populateMimeMessageBody(MimeMessage message, String body) throws MessagingException {
        MimeBodyPart plainPart = new MimeBodyPart();
        plainPart.setText(body);

        MimeBodyPart htmlPart = new MimeBodyPart();
        htmlPart.setText("<html><body>" + body + "</body></html>");

        Multipart alt = new MimeMultipart("alternative");
        alt.addBodyPart(plainPart);
        alt.addBodyPart(htmlPart);

        Multipart mixed = new MimeMultipart("mixed");
        MimeBodyPart wrap = new MimeBodyPart();
        wrap.setContent(alt);
        mixed.addBodyPart(wrap);

        mixed.addBodyPart(plainPart);
        mixed.addBodyPart(htmlPart);

        mixed.addBodyPart(plainPart);
        mixed.addBodyPart(htmlPart);

        message.setContent(mixed);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            public void configure() {
                getContext().getRegistry().bind("authPop3", new MyAuthenticator("pop3"));
                getContext().getRegistry().bind("authSmtp", new MyAuthenticator("smtp"));
                getContext().getRegistry().bind("authImap", new MyAuthenticator("imap"));

                onException(MessagingException.class).handled(true).to("mock:exception");

                from("pop3://localhost?initialDelay=100&delay=100&authenticator=#authPop3").removeHeader("to")
                        .to("smtp://localhost?authenticator=#authSmtp&to=james4@localhost");
                from("imap://localhost?initialDelay=200&delay=100&authenticator=#authImap").convertBodyTo(String.class).to("mock:result");
            }
        };
    }

    public static class MyAuthenticator extends MailAuthenticator {
        private final String protocol;
        private int counter;

        public MyAuthenticator(String protocol) {
            this.protocol = protocol;
        }

        @Override
        public PasswordAuthentication getPasswordAuthentication() {
            if ("pop3".equals(protocol)) {
                return new PasswordAuthentication("james3", "secret");
            } else if ("smtp".equals(protocol)) {
                if (counter < 2) {
                    // in the processing of a mail message the mail consumer calls this method twice
                    counter++;
                    return new PasswordAuthentication("james4", "secret");
                } else if (counter < 4) {
                    // return in the second call the wrongPassword which will throw an MessagingException, see MyMockTransport
                    counter++;
                    return new PasswordAuthentication("james4", "wrongPassword");
                } else {
                    return new PasswordAuthentication("james4", "secret");
                }
            } else if ("imap".equals(protocol)) {
                return new PasswordAuthentication("james4", "secret");
            } else {
                throw new IllegalStateException("not supported protocol " + protocol);
            }

        }
    }
}
