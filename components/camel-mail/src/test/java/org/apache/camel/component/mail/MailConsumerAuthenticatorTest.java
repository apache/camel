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
import java.util.stream.Stream;

import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;

import org.apache.camel.CamelContext;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.Processor;
import org.apache.camel.component.mail.Mailbox.MailboxUser;
import org.apache.camel.component.mail.Mailbox.Protocol;
import org.apache.camel.spi.ExchangeFactory;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Test the dynamic behavior of the MailAuthenticator in the MailConsumer.
 */
public class MailConsumerAuthenticatorTest {
    private static final MailboxUser user1 = Mailbox.getOrCreateUser("user1", "correctPassword");

    @Test
    public void dynamicPasswordPop3() throws Exception {
        execute(Protocol.pop3);
    }

    @Test
    public void dynamicPasswordImap() throws Exception {
        execute(Protocol.imap);
    }

    private void execute(Protocol protocol) throws Exception {
        MyAuthenticator authenticator = new MyAuthenticator(user1.getLogin(), "badPassword");

        JavaMailSender sender = Mockito.mock(JavaMailSender.class);
        Processor processor = Mockito.mock(Processor.class);
        CamelContext camelContext = Mockito.mock(CamelContext.class);
        ExtendedCamelContext ecc = Mockito.mock(ExtendedCamelContext.class);
        ExchangeFactory ef = Mockito.mock(ExchangeFactory.class);

        MailEndpoint endpoint = new MailEndpoint();
        endpoint.setCamelContext(camelContext);
        MailConfiguration configuration = new MailConfiguration();
        configuration.setAuthenticator(authenticator);
        configuration.configureProtocol(protocol.name());
        configuration.setPort(Mailbox.getPort(protocol));
        configuration.setFolderName("INBOX");

        endpoint.setConfiguration(configuration);

        Properties props = Mailbox.getSessionProperties(protocol);
        props.putAll(Mailbox.getSessionProperties(Protocol.smtp));
        Session session = Session.getInstance(props, authenticator);

        when(sender.getSession()).thenReturn(session);
        when(camelContext.getCamelContextExtension()).thenReturn(ecc);
        when(ecc.getExchangeFactory()).thenReturn(ef);
        when(ef.newExchangeFactory(any())).thenReturn(ef);

        MailConsumer consumer = new MailConsumer(endpoint, processor, sender);
        try {
            Assertions.assertThatThrownBy(() -> consumer.poll())
                    .isInstanceOf(MessagingException.class)
                    .message().matches(actualMessage -> Stream.of("LOGIN failed. Invalid login/password for user id",
                            "Authentication failed: com.icegreen.greenmail.user.UserException: Invalid password")
                            .anyMatch(expectedSubstring -> actualMessage.contains(expectedSubstring)));

            // poll a second time, this time there should be no exception, because we now provide the correct password
            authenticator.setPassword(user1.getPassword());
            consumer.poll();
        } finally {
            consumer.close();
        }
    }

    public static class MyAuthenticator extends MailAuthenticator {

        private final String user;
        private String password;

        public MyAuthenticator(String user, String password) {
            super();
            this.user = user;
            this.password = password;
        }

        public PasswordAuthentication getPasswordAuthentication() {
            return new PasswordAuthentication(user, password);
        }

        public void setPassword(String password) {
            this.password = password;
        }

    }

}
