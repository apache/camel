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

import java.util.HashMap;
import java.util.Properties;

import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MailBindingAttachmentDuplicateFileNameTest {

    private final MailBinding binding = new MailBinding();

    @Test
    public void shouldFailOnDuplicateFilenames() throws Exception {
        binding.setFailOnDuplicateAttachment(true);

        final Session session = Session.getInstance(new Properties());
        final Message message = new MimeMessage(session);

        String filename = "file.txt";
        String filename2 = "file.txt";
        setAttachments(message, filename, filename2);
        extractAttachmentsAndValidate(message, filename, filename2);

        filename = "file.txt";
        filename2 = "../file.txt";
        setAttachments(message, filename, filename2);
        extractAttachmentsAndValidate(message, filename, filename2);

        filename = "file.txt";
        filename2 = "..\\file.txt";
        setAttachments(message, filename, filename2);
        extractAttachmentsAndValidate(message, filename, filename2);

        filename = "file.txt";
        filename2 = "/absolute/file.txt";
        setAttachments(message, filename, filename2);
        extractAttachmentsAndValidate(message, filename, filename2);

        filename = "file.txt";
        filename2 = "c:\\absolute\\file.txt";
        setAttachments(message, filename, filename2);
        extractAttachmentsAndValidate(message, filename, filename2);

        filename = "..\\file.txt";
        filename2 = "c:\\absolute\\file.txt";
        setAttachments(message, filename, filename2);
        extractAttachmentsAndValidate(message, filename, filename2);
    }

    private void extractAttachmentsAndValidate(Message message, String filename, String filename2) throws Exception {
        MessagingException thrown;
        setAttachments(message, filename, filename2);
        thrown = assertThrows(MessagingException.class, () -> binding.extractAttachmentsFromMail(message, new HashMap<>()),
                "Duplicate attachment names should cause an exception");
        assertTrue(thrown.getMessage().contains("Duplicate file attachment"));
    }

    private void setAttachments(Message message, String name1, String name2) throws Exception {
        Multipart multipart = new MimeMultipart();
        MimeBodyPart part = new MimeBodyPart();
        part.attachFile(name1);
        multipart.addBodyPart(part);

        part = new MimeBodyPart();
        part.attachFile(name2);
        multipart.addBodyPart(part);

        message.setContent(multipart);
    }

}
