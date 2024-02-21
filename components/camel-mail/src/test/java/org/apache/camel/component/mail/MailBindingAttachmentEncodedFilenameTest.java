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
import java.util.Map;
import java.util.Properties;

import jakarta.activation.DataHandler;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.internet.MimeUtility;

import org.apache.camel.attachment.Attachment;
import org.apache.camel.support.DefaultHeaderFilterStrategy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MailBindingAttachmentEncodedFilenameTest {

    // enable file decoding
    private final MailBinding binding = new MailBinding(new DefaultHeaderFilterStrategy(), null, true, true);

    @Test
    public void shouldNotBreakEncodedFileNamesWithSlashes() throws Exception {
        final Session session = Session.getInstance(new Properties());
        final Message message = new MimeMessage(session);
        final String encodedFilename = "=?UTF-8?B?6Kq/5pW0?=";
        final String plainFilename = MimeUtility.decodeText(encodedFilename);

        final Multipart multipart = new MimeMultipart();
        final MimeBodyPart part = new MimeBodyPart();
        part.attachFile(plainFilename);
        part.setFileName(encodedFilename);
        part.setDisposition(Part.ATTACHMENT);
        multipart.addBodyPart(part);
        message.setContent(multipart);

        final Map<String, Attachment> attachments = new HashMap<>();
        binding.extractAttachmentsFromMail(message, attachments);

        assertTrue(attachments.containsKey(plainFilename));
        final Attachment attachment = attachments.get(plainFilename);
        final DataHandler dataHandler = attachment.getDataHandler();
        assertEquals(plainFilename, dataHandler.getName());
    }
}
