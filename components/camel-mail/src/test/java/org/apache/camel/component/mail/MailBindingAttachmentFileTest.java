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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.activation.DataHandler;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import org.apache.camel.attachment.Attachment;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(Parameterized.class)
public class MailBindingAttachmentFileTest {

    @Parameter
    public String name;

    private final MailBinding binding = new MailBinding();

    @Test
    public void shouldSanitizeAttachmentFileNames() throws MessagingException, IOException {
        final Session session = Session.getInstance(new Properties());
        final Message message = new MimeMessage(session);

        final Multipart multipart = new MimeMultipart();
        final MimeBodyPart part = new MimeBodyPart();
        part.attachFile(name);
        multipart.addBodyPart(part);
        message.setContent(multipart);

        final Map<String, Attachment> attachments = new HashMap<>();
        binding.extractAttachmentsFromMail(message, attachments);

        assertThat(attachments).containsKey("file.txt");
        final Attachment attachment = attachments.get("file.txt");
        final DataHandler dataHandler = attachment.getDataHandler();
        assertThat(dataHandler.getName()).isEqualTo("file.txt");
    }

    @Parameters(name = "{0}")
    public static Iterable<String> fileNames() {
        return Arrays.asList("file.txt", "../file.txt", "..\\file.txt", "/absolute/file.txt", "c:\\absolute\\file.txt");
    }
}
