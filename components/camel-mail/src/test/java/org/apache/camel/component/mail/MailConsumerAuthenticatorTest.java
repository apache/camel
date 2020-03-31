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

import java.io.IOException;
import java.util.Properties;

import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;

import org.apache.camel.Processor;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Test the dynamic behavior of the MailAuthenticator in the MailConsumer.
 */
public class MailConsumerAuthenticatorTest {

    @Test
    public void dynamicPasswordPop3() throws Exception {
        execute("pop3");
    }

    @Test
    public void dynamicPasswordImap() throws Exception {
        execute("imap");
    }

    private void execute(String protocol) throws Exception, IOException {
        MailAuthenticator authenticator = new MyAuthenticator();

        JavaMailSender sender = Mockito.mock(JavaMailSender.class);
        Processor processor = Mockito.mock(Processor.class);

        MailEndpoint endpoint = new MailEndpoint();
        MailConfiguration configuration = new MailConfiguration();
        configuration.setAuthenticator(authenticator);
        configuration.configureProtocol(protocol);
        configuration.setFolderName("INBOX");

        endpoint.setConfiguration(configuration);

        Properties props = new Properties();
        props.put("mail.store.protocol", protocol);
        Session session = Session.getDefaultInstance(props, authenticator);

        when(sender.getSession()).thenReturn(session);

        MailConsumer consumer = new MailConsumer(endpoint, processor, sender);
        try {
            boolean exception = false;
            try {
                consumer.poll();
            } catch (MessagingException e) {
                // we expect that an Exception occurs with the worngPassword, see MyMockStore
                assertEquals("unauthorized", e.getMessage());
                exception = true;
            }
            assertTrue("MessagingException expected with message 'unauthorized'", exception);

            // poll a second time, this time there should be no exception, because we now provide the correct password
            consumer.poll();
        } finally {
            consumer.close();
        }
    }

    public static class MyAuthenticator extends MailAuthenticator {

        private int counter;

        public PasswordAuthentication getPasswordAuthentication() {
            if (counter == 0) {
                counter++;
                // the first time return a wrong password
                return new PasswordAuthentication("user1", "wrongPassword");
            }
            //otherwise return the correct password, to simulate dynamic behavior
            return new PasswordAuthentication("user1", "correctPassword");
        }

    }

}
