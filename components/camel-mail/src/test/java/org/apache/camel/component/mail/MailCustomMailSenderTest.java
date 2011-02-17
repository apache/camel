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
import javax.mail.internet.MimeMessage;

import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessagePreparator;

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

    private class MySender implements JavaMailSender {

        public void send(SimpleMailMessage simpleMessage) throws MailException {
            // noop
        }

        public void send(SimpleMailMessage[] simpleMessages) throws MailException {
            // noop
        }

        public MimeMessage createMimeMessage() {
            return null;
        }

        public MimeMessage createMimeMessage(InputStream contentStream) throws MailException {
            return null;
        }

        public void send(MimeMessage mimeMessage) throws MailException {
            // noop
        }

        public void send(MimeMessage[] mimeMessages) throws MailException {
            // noop
        }

        public void send(MimeMessagePreparator mimeMessagePreparator) throws MailException {
            sent = true;
        }

        public void send(MimeMessagePreparator[] mimeMessagePreparators) throws MailException {
            // noop
        }
    }

}
