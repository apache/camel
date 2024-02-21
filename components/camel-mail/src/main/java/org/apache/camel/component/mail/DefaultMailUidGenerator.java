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

import java.util.Enumeration;
import java.util.UUID;

import jakarta.mail.Header;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;

import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultMailUidGenerator implements MailUidGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultMailUidGenerator.class);

    @Override
    public String generateUuid(MailEndpoint mailEndpoint, Message message) {
        String answer = generateMessageIdHeader(message);
        if (answer == null) {
            answer = generateMessageHash(message);
        }
        // fallback and use message number
        if (answer == null || ObjectHelper.isEmpty(answer)) {
            answer = Integer.toString(message.getMessageNumber());
        }
        return answer;
    }

    private String generateMessageIdHeader(Message message) {
        LOG.trace("generateMessageIdHeader for msg: {}", message);

        // there should be a Message-ID header with the UID
        try {
            String[] values = message.getHeader("Message-ID");
            if (values != null && values.length > 0) {
                String uid = values[0];
                LOG.trace("Message-ID header found: {}", uid);
                return uid;
            }
        } catch (MessagingException e) {
            LOG.warn("Cannot read headers from mail message. This exception will be ignored.", e);
        }

        return null;
    }

    public String generateMessageHash(Message message) {
        LOG.trace("generateMessageHash for msg: {}", message);

        String uid = null;

        // create an UID based on message headers on the message, that ought to be unique
        StringBuilder buffer = new StringBuilder();
        try {
            Enumeration<?> it = message.getAllHeaders();
            while (it.hasMoreElements()) {
                Header header = (Header) it.nextElement();
                buffer.append(header.getName()).append("=").append(header.getValue()).append("\n");
            }
            if (buffer.length() > 0) {
                LOG.trace("Generating UID from the following:\n {}", buffer);
                uid = UUID.nameUUIDFromBytes(buffer.toString().getBytes()).toString();
            }
        } catch (MessagingException e) {
            LOG.warn("Cannot read headers from mail message. This exception will be ignored.", e);
        }

        return uid;
    }
}
