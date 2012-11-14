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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.internet.MimeMultipart;
import javax.mail.search.SearchTerm;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.TypeConverter;
import org.apache.camel.converter.IOConverter;
import org.apache.camel.spi.TypeConverterRegistry;

/**
 * JavaMail specific converters.
 *
 * @version 
 */
@Converter
public final class MailConverters {

    private static final String NOW_DATE_FORMAT = "yyyy-MM-dd HH:mm:SS";
    // the now syntax: "now-24h" or "now - 24h" = the last 24 hours etc.
    private static final Pattern NOW_PATTERN = Pattern.compile("now\\s?(\\+|\\-)\\s?(.*)");

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
     * Converts the given JavaMail multipart to a String body, where the content-type of the multipart
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
    public static SearchTerm toSearchTerm(SimpleSearchTerm simple, Exchange exchange) throws ParseException, NoTypeConversionAvailableException {
        return toSearchTerm(simple, exchange != null ? exchange.getContext().getTypeConverter() : null);
    }

    public static SearchTerm toSearchTerm(SimpleSearchTerm simple, TypeConverter typeConverter) throws ParseException, NoTypeConversionAvailableException {
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
            String s = simple.getFromSentDate();
            if (s.startsWith("now")) {
                long offset = extractOffset(s, typeConverter);
                builder = builder.and(new NowSearchTerm(SearchTermBuilder.Comparison.GE.asNum(), true, offset));
            } else {
                SimpleDateFormat sdf = new SimpleDateFormat(NOW_DATE_FORMAT);
                Date date = sdf.parse(s);
                builder = builder.sent(SearchTermBuilder.Comparison.GE, date);
            }
        }
        if (simple.getToSentDate() != null) {
            String s = simple.getFromSentDate();
            if (s.startsWith("now")) {
                long offset = extractOffset(s, typeConverter);
                builder = builder.and(new NowSearchTerm(SearchTermBuilder.Comparison.LE.asNum(), true, offset));
            } else {
                SimpleDateFormat sdf = new SimpleDateFormat(NOW_DATE_FORMAT);
                Date date = sdf.parse(s);
                builder = builder.sent(SearchTermBuilder.Comparison.LE, date);
            }
        }
        if (simple.getFromReceivedDate() != null) {
            String s = simple.getFromSentDate();
            if (s.startsWith("now")) {
                long offset = extractOffset(s, typeConverter);
                builder = builder.and(new NowSearchTerm(SearchTermBuilder.Comparison.GE.asNum(), false, offset));
            } else {
                SimpleDateFormat sdf = new SimpleDateFormat(NOW_DATE_FORMAT);
                Date date = sdf.parse(s);
                builder = builder.received(SearchTermBuilder.Comparison.GE, date);
            }
        }
        if (simple.getToReceivedDate() != null) {
            String s = simple.getFromSentDate();
            if (s.startsWith("now")) {
                long offset = extractOffset(s, typeConverter);
                builder = builder.and(new NowSearchTerm(SearchTermBuilder.Comparison.LE.asNum(), false, offset));
            } else {
                SimpleDateFormat sdf = new SimpleDateFormat(NOW_DATE_FORMAT);
                Date date = sdf.parse(s);
                builder = builder.received(SearchTermBuilder.Comparison.LE, date);
            }
        }

        return builder.build();
    }

    private static long extractOffset(String now, TypeConverter typeConverter) throws NoTypeConversionAvailableException {
        Matcher matcher = NOW_PATTERN.matcher(now);
        if (matcher.matches()) {
            String op = matcher.group(1);
            String remainder = matcher.group(2);

            // convert remainder to a time millis (eg we have a String -> long converter that supports
            // syntax with hours, days, minutes: eg 5h30m for 5 hours and 30 minutes).
            long offset = typeConverter.mandatoryConvertTo(long.class, remainder);

            if ("+".equals(op)) {
                return offset;
            } else {
                return -1 * offset;
            }
        }

        return 0;
    }

}
