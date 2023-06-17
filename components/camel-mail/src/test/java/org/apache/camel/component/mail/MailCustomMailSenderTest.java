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

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

import org.apache.camel.BindToRegistry;
import org.apache.camel.component.mail.Mailbox.MailboxUser;
import org.apache.camel.component.mail.Mailbox.Protocol;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class MailCustomMailSenderTest extends CamelTestSupport {
    private static final MailboxUser claus = Mailbox.getOrCreateUser("claus", "secret");

    private static boolean sent;

    @BindToRegistry("mySender")
    private MySender sender = new MySender();

    @Test
    public void testSendWithCustomMailSender() {
        sendBody(claus.uriPrefix(Protocol.smtp) + "&javaMailSender=#mySender", "Hello World");

        assertTrue(sent, "Should have used custom mail sender");
    }

    private static class MySender implements JavaMailSender {

        @Override
        public void send(MimeMessage mimeMessage) {
            sent = true;
        }

        @Override
        public Properties getJavaMailProperties() {
            return null;
        }

        @Override
        public void addAdditionalJavaMailProperty(String key, String value) {
        }

        @Override
        public void setJavaMailProperties(Properties javaMailProperties) {
        }

        @Override
        public void setHost(String host) {
        }

        @Override
        public String getHost() {
            return null;
        }

        @Override
        public void setPort(int port) {
        }

        @Override
        public int getPort() {
            return 0;
        }

        @Override
        public void setUsername(String username) {
        }

        @Override
        public String getUsername() {
            return null;
        }

        @Override
        public void setPassword(String password) {
        }

        @Override
        public String getPassword() {
            return null;
        }

        @Override
        public void setProtocol(String protocol) {
        }

        @Override
        public String getProtocol() {
            return null;
        }

        @Override
        public void setSession(Session session) {
        }

        @Override
        public Session getSession() {
            return null;
        }

        @Override
        public void setAuthenticator(MailAuthenticator authenticator) {
        }

        @Override
        public MailAuthenticator getAuthenticator() {
            return null;
        }
    }

}
