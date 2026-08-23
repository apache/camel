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

import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

import org.apache.camel.attachment.Attachment;
import org.eclipse.angus.mail.imap.SortTerm;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.Mockito.when;

/**
 * A single malformed or merely unusual message must not put the consumer into a state it never recovers from. Each of
 * these previously aborted or hung the poll, and repeated on every subsequent poll until the message was removed out of
 * band.
 */
class MailPoisonMessageTest {

    @Test
    void sortingToleratesAMessageWithoutTheSortedOnHeader() throws Exception {
        // Subject is optional in RFC 5322, so a message may legitimately carry none
        Message withSubject = mockMessage("subject");
        Message withoutSubject = mockMessage(null);

        Message[] messages = { withSubject, withoutSubject };
        assertDoesNotThrow(() -> MailSorter.sortMessages(messages, new SortTerm[] { SortTerm.SUBJECT }));
    }

    @Test
    void deeplyNestedMultipartDoesNotExhaustTheStack() {
        assertDoesNotThrow(() -> {
            MimeMultipart nested = new MimeMultipart();
            MimeBodyPart leaf = new MimeBodyPart();
            leaf.setText("payload");
            nested.addBodyPart(leaf);

            for (int i = 0; i < 8000; i++) {
                MimeBodyPart wrapper = new MimeBodyPart();
                wrapper.setContent(nested);
                // setContent(Multipart) does not update the part's Content-Type header until the
                // enclosing message is saved, and MailBinding dispatches on isMimeType("multipart/*"),
                // so the header has to be set explicitly for this to exercise the recursion at all
                wrapper.setHeader("Content-Type", nested.getContentType());
                MimeMultipart outer = new MimeMultipart();
                outer.addBodyPart(wrapper);
                nested = outer;
            }

            Map<String, Attachment> map = new HashMap<>();
            new MailBinding().extractAttachmentsFromMultipart(nested, map);
        });
    }

    @Test
    void emptyMultipartDoesNotSpinForever() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            MimeMessage message = new MimeMessage(Session.getDefaultInstance(new java.util.Properties()));
            message.setContent(new MimeMultipart());

            assertNull(MailConverters.toString(message));
        });
    }

    private static Message mockMessage(String subject) throws MessagingException {
        Message msg = Mockito.mock(Message.class);
        when(msg.getFrom()).thenReturn(new Address[] { new InternetAddress("from@localhost") });
        when(msg.getRecipients(Message.RecipientType.TO))
                .thenReturn(new Address[] { new InternetAddress("to@localhost") });
        when(msg.getSentDate()).thenReturn(new Date(1));
        when(msg.getReceivedDate()).thenReturn(new Date(1));
        when(msg.getSize()).thenReturn(1);
        when(msg.getSubject()).thenReturn(subject);
        return msg;
    }
}
