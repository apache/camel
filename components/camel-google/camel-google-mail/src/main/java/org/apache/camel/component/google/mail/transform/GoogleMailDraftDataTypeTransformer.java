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
package org.apache.camel.component.google.mail.transform;

import java.io.ByteArrayOutputStream;
import java.util.Properties;

import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import com.google.api.client.util.Base64;
import com.google.api.services.gmail.model.Draft;
import com.google.api.services.gmail.model.Message;
import org.apache.camel.Exchange;
import org.apache.camel.component.google.mail.stream.GoogleMailStreamConstants;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.DataTypeTransformer;
import org.apache.camel.spi.Transformer;
import org.apache.camel.util.ObjectHelper;

import static jakarta.mail.Message.RecipientType.BCC;
import static jakarta.mail.Message.RecipientType.CC;
import static jakarta.mail.Message.RecipientType.TO;

/**
 * Data type transformer that builds a {@link Draft} from a String body and mail headers populated by
 * google-mail-stream.
 */
@DataTypeTransformer(name = "google-mail:draft",
                     description = "Creates a Gmail Draft from String body and threading metadata from headers")
public class GoogleMailDraftDataTypeTransformer extends Transformer {

    @Override
    public void transform(org.apache.camel.Message message, DataType fromType, DataType toType) throws Exception {
        String body = message.getBody(String.class);
        if (ObjectHelper.isEmpty(body)) {
            throw new IllegalArgumentException("Draft body must not be null or empty");
        }

        String threadId = message.getHeader(GoogleMailStreamConstants.MAIL_THREAD_ID, String.class);
        String messageId = message.getHeader(GoogleMailStreamConstants.MAIL_MESSAGE_ID, String.class);
        String from = message.getHeader(GoogleMailStreamConstants.MAIL_FROM, String.class);
        String subject = message.getHeader(GoogleMailStreamConstants.MAIL_SUBJECT, String.class);
        String to = message.getHeader(GoogleMailStreamConstants.MAIL_TO, String.class);
        String cc = message.getHeader(GoogleMailStreamConstants.MAIL_CC, String.class);
        String bcc = message.getHeader(GoogleMailStreamConstants.MAIL_BCC, String.class);

        Session session = Session.getDefaultInstance(new Properties());
        MimeMessage mimeMessage = new MimeMessage(session);

        if (ObjectHelper.isNotEmpty(from)) {
            mimeMessage.setFrom(new InternetAddress(from, true));
        }
        if (ObjectHelper.isNotEmpty(to)) {
            mimeMessage.addRecipients(TO, InternetAddress.parse(to, true));
        }
        if (ObjectHelper.isNotEmpty(cc)) {
            mimeMessage.addRecipients(CC, InternetAddress.parse(cc, true));
        }
        if (ObjectHelper.isNotEmpty(bcc)) {
            mimeMessage.addRecipients(BCC, InternetAddress.parse(bcc, true));
        }
        if (ObjectHelper.isNotEmpty(subject)) {
            mimeMessage.setSubject(subject, "UTF-8");
        }

        if (ObjectHelper.isNotEmpty(messageId)) {
            mimeMessage.setHeader("In-Reply-To", messageId);
            mimeMessage.setHeader("References", messageId);
        }

        String contentType = message.getHeader(Exchange.CONTENT_TYPE, String.class);
        if (contentType == null) {
            contentType = "text/plain; charset=UTF-8";
        }
        mimeMessage.setContent(body, contentType);

        String encodedEmail;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            mimeMessage.writeTo(baos);
            encodedEmail = Base64.encodeBase64URLSafeString(baos.toByteArray());
        }

        Message gmailMessage = new Message();
        gmailMessage.setRaw(encodedEmail);

        if (ObjectHelper.isNotEmpty(threadId)) {
            gmailMessage.setThreadId(threadId);
        }

        Draft draft = new Draft();
        draft.setMessage(gmailMessage);

        message.setBody(draft);
    }
}
