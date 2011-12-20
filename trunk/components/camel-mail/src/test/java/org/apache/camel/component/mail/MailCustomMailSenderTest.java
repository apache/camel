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

import java.util.Properties;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;

import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * @version 
 */
public class MailCustomMailSenderTest extends CamelTestSupport {

    private static boolean sent;

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("mySender", new MySender());
        return jndi;
    }

    @Test
    public void testSendWithCustomMailSender() throws Exception {
        sendBody("smtp://claus@localhost?javaMailSender=#mySender", "Hello World");

        assertTrue("Should have used custom mail sender", sent);
    }

    private static class MySender implements JavaMailSender {

        @Override
        public void send(MimeMessage mimeMessage) throws MessagingException {
            sent = true;
        }

        @Override
        public Properties getJavaMailProperties() {
            return null;
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
    }

}
