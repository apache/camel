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

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.internet.MimeMultipart;
import javax.mail.search.SearchTerm;

import org.apache.camel.Converter;
import org.apache.camel.converter.IOConverter;

/**
 * JavaMail specific converters.
 *
 * @version 
 */
@Converter
public final class MailConverters {
    
    private MailConverters() {
        //Utility Class
    }

    /**
     * Converts the given JavaMail message to a String body.
     * Can return null.
     */
    @Converter
    public static String toString(Message message) throws MessagingException, IOException {
        Object content = message.getContent();
        if (content instanceof MimeMultipart) {
            MimeMultipart multipart = (MimeMultipart) content;
            if (multipart.getCount() > 0) {
                BodyPart part = multipart.getBodyPart(0);
                content = part.getContent();
            }
        }
        if (content != null) {
            return content.toString();
        }
        return null;
    }

    /**
     * Converts the given JavaMail multipart to a String body, where the contenttype of the multipart
     * must be text based (ie start with text). Can return null.
     */
    @Converter
    public static String toString(Multipart multipart) throws MessagingException, IOException {
        int size = multipart.getCount();
        for (int i = 0; i < size; i++) {
            BodyPart part = multipart.getBodyPart(i);
            if (part.getContentType().startsWith("text")) {
                return part.getContent().toString();
            }
        }
        return null;
    }

    /**
     * Converts the given JavaMail message to an InputStream.
     */
    @Converter
    public static InputStream toInputStream(Message message) throws IOException, MessagingException {
        return message.getInputStream();
    }

    /**
     * Converts the given JavaMail multipart to a InputStream body, where the contenttype of the multipart
     * must be text based (ie start with text). Can return null.
     */
    @Converter
    public static InputStream toInputStream(Multipart multipart) throws IOException, MessagingException {
        String s = toString(multipart);
        if (s == null) {
            return null;
        }
        return IOConverter.toInputStream(s, null);
    }

    @Converter
    public static SearchTerm toSearchTerm(SimpleSearchTerm simple) throws ParseException {
        SearchTermBuilder builder = new SearchTermBuilder();
        if (simple.isUnseen()) {
            builder = builder.unseen();
        }

        if (simple.getSubjectOrBody() != null) {
            String text = simple.getSubjectOrBody();
            builder = builder.subject(text).body(SearchTermBuilder.Op.or, text);
        }
        if (simple.getSubject() != null) {
            builder = builder.subject(simple.getSubject());
        }
        if (simple.getBody() != null) {
            builder = builder.body(simple.getBody());
        }
        if (simple.getFrom() != null) {
            builder = builder.from(simple.getFrom());
        }
        if (simple.getTo() != null) {
            builder = builder.recipient(Message.RecipientType.TO, simple.getTo());
        }
        if (simple.getFromSentDate() != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:SS");
            Date date;
            if ("now".equals(simple.getFromSentDate())) {
                date = new Date();
            } else {
                date = sdf.parse(simple.getFromSentDate());
            }
            builder = builder.sent(SearchTermBuilder.Comparison.GE, date);
        }
        if (simple.getToSentDate() != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:SS");
            Date date;
            if ("now".equals(simple.getToSentDate())) {
                date = new Date();
            } else {
                date = sdf.parse(simple.getToSentDate());
            }
            builder = builder.sent(SearchTermBuilder.Comparison.LE, date);
        }

        return builder.build();
    }

}
