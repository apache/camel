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

import java.io.UnsupportedEncodingException;

import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Store;
import jakarta.mail.internet.MimeMessage;

import org.apache.camel.component.mail.Mailbox.MailboxUser;
import org.apache.camel.component.mail.Mailbox.Protocol;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class MailConsumerUnsupportedCharsetTest extends CamelTestSupport {
    private static final MailboxUser jones = Mailbox.getOrCreateUser("jones", "secret");

    @Test
    public void testConsumeUnsupportedCharset() throws Exception {
        JavaMailSender sender = new DefaultJavaMailSender();
        Store store = sender.getSession().getStore("imap");
        store.connect("localhost", Mailbox.getPort(Protocol.imap), jones.getLogin(), jones.getPassword());
        Folder folder = store.getFolder("INBOX");
        folder.open(Folder.READ_WRITE);
        folder.expunge();

        Message[] msg = new Message[1];
        MimeMessage mime = new MimeMessage(sender.getSession());
        mime.setContent("Bye World", "text/plain; charset=ThisIsNotAKnownCharset");
        msg[0] = mime;
        try {
            Assertions.assertThatThrownBy(() -> folder.appendMessages(msg)).isInstanceOf(MessagingException.class)
                    .hasCauseInstanceOf(UnsupportedEncodingException.class);
        } finally {
            folder.close(true);
        }
    }

}
