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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.search.SearchTerm;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.TypeConverter;
import org.apache.camel.spi.TypeConverterRegistry;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.util.TimeUtils;
import org.eclipse.angus.mail.imap.SortTerm;

/**
 * JavaMail specific converters.
 */
@Converter(generateLoader = true)
public final class MailConverters {

    private static final String NOW_DATE_FORMAT = "yyyy-MM-dd HH:mm:SS";
    // the now syntax: "now-24h" or "now - 24h" = the last 24 hours etc.
    private static final Pattern NOW_PATTERN = Pattern.compile("now\\s?(\\+|\\-)\\s?(.*)");

    private MailConverters() {
        //Utility Class
    }

    /**
     * Converts the given JavaMail message to a String body. Can return null.
     */
    @Converter
    public static String toString(Message message) throws MessagingException, IOException {
        Object content = message.getContent();
        while (content instanceof MimeMultipart) {
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
     * Converts the given JavaMail multipart to a String body, where the content-type of the multipart must be text
     * based (ie start with text). Can return null.
     */
    @Converter
    public static String toString(Multipart multipart) throws MessagingException, IOException {
        int size = multipart.getCount();
        for (int i = 0; i < size; i++) {
            BodyPart part = multipart.getBodyPart(i);
            Object content = part.getContent();
            while (content instanceof MimeMultipart) {
                if (multipart.getCount() < 1) {
                    break;
                }
                part = ((MimeMultipart) content).getBodyPart(0);
                content = part.getContent();
            }
            // Perform a case insensitive "startsWith" check that works for different locales
            String prefix = "text";
            if (part.getContentType().regionMatches(true, 0, prefix, 0, prefix.length())) {
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
     * Converts the given JavaMail multipart to a InputStream body, where the content-type of the multipart must be text
     * based (ie start with text). Can return null.
     */
    @Converter
    public static InputStream toInputStream(Multipart multipart, Exchange exchange) throws IOException, MessagingException {
        String s = toString(multipart);
        if (s == null) {
            return null;
        }
        return new ByteArrayInputStream(s.getBytes(ExchangeHelper.getCharsetName(exchange)));
    }

    /**
     * Converts a JavaMail multipart into a body of any type a String can be converted into. The content-type of the
     * part must be text based.
     */
    @Converter(fallback = true)
    public static <T> T convertTo(Class<T> type, Exchange exchange, Object value, TypeConverterRegistry registry)
            throws MessagingException, IOException {
        if (Multipart.class.isAssignableFrom(value.getClass())) {
            TypeConverter tc = registry.lookup(type, String.class);
            if (tc != null) {
                String s = toString((Multipart) value);
                if (s != null) {
                    return tc.convertTo(type, s);
                }
            }
        }
        return null;
    }

    /**
     * Converters the simple search term builder to search term.
     *
     * This should not be a @Converter method
     */
    public static SearchTerm toSearchTerm(SimpleSearchTerm simple) throws ParseException {
        SearchTermBuilder builder = new SearchTermBuilder();
        if (simple.isUnseen()) {
            builder = builder.unseen();
        }

        if (simple.getSubjectOrBody() != null) {
            String text = simple.getSubjectOrBody();
            SearchTermBuilder builderTemp = new SearchTermBuilder();
            builderTemp = builderTemp.subject(text).body(SearchTermBuilder.Op.or, text);
            builder = builder.and(builderTemp.build());
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
                long offset = extractOffset(s);
                builder = builder.and(new NowSearchTerm(SearchTermBuilder.Comparison.GE.asNum(), true, offset));
            } else {
                SimpleDateFormat sdf = new SimpleDateFormat(NOW_DATE_FORMAT);
                Date date = sdf.parse(s);
                builder = builder.sent(SearchTermBuilder.Comparison.GE, date);
            }
        }
        if (simple.getToSentDate() != null) {
            String s = simple.getToSentDate();
            if (s.startsWith("now")) {
                long offset = extractOffset(s);
                builder = builder.and(new NowSearchTerm(SearchTermBuilder.Comparison.LE.asNum(), true, offset));
            } else {
                SimpleDateFormat sdf = new SimpleDateFormat(NOW_DATE_FORMAT);
                Date date = sdf.parse(s);
                builder = builder.sent(SearchTermBuilder.Comparison.LE, date);
            }
        }
        if (simple.getFromReceivedDate() != null) {
            String s = simple.getFromReceivedDate();
            if (s.startsWith("now")) {
                long offset = extractOffset(s);
                builder = builder.and(new NowSearchTerm(SearchTermBuilder.Comparison.GE.asNum(), false, offset));
            } else {
                SimpleDateFormat sdf = new SimpleDateFormat(NOW_DATE_FORMAT);
                Date date = sdf.parse(s);
                builder = builder.received(SearchTermBuilder.Comparison.GE, date);
            }
        }
        if (simple.getToReceivedDate() != null) {
            String s = simple.getToReceivedDate();
            if (s.startsWith("now")) {
                long offset = extractOffset(s);
                builder = builder.and(new NowSearchTerm(SearchTermBuilder.Comparison.LE.asNum(), false, offset));
            } else {
                SimpleDateFormat sdf = new SimpleDateFormat(NOW_DATE_FORMAT);
                Date date = sdf.parse(s);
                builder = builder.received(SearchTermBuilder.Comparison.LE, date);
            }
        }

        return builder.build();
    }

    /*
     * Converts from comma separated list of sort terms to SortTerm obj array.
     * This should not be a @Converter method
     */
    public static SortTerm[] toSortTerm(String sortTerm) {
        ArrayList<SortTerm> result = new ArrayList<>();

        if (sortTerm == null) {
            return null;
        }

        String[] sortTerms = sortTerm.split(",");
        for (String key : sortTerms) {
            if ("arrival".equals(key)) {
                result.add(SortTerm.ARRIVAL);
            } else if ("cc".equals(key)) {
                result.add(SortTerm.CC);
            } else if ("date".equals(key)) {
                result.add(SortTerm.DATE);
            } else if ("from".equals(key)) {
                result.add(SortTerm.FROM);
            } else if ("reverse".equals(key)) {
                result.add(SortTerm.REVERSE);
            } else if ("size".equals(key)) {
                result.add(SortTerm.SIZE);
            } else if ("subject".equals(key)) {
                result.add(SortTerm.SUBJECT);
            } else if ("to".equals(key)) {
                result.add(SortTerm.TO);
            }
        }
        if (!result.isEmpty()) {
            return result.toArray(new SortTerm[0]);
        } else {
            return null;
        }
    }

    private static long extractOffset(String now) {
        Matcher matcher = NOW_PATTERN.matcher(now);
        if (matcher.matches()) {
            String op = matcher.group(1);
            String remainder = matcher.group(2);

            // convert remainder to a time millis (eg we have a String -> long converter that supports
            // syntax with hours, days, minutes: eg 5h30m for 5 hours and 30 minutes).
            long offset = TimeUtils.toMilliSeconds(remainder);

            if ("+".equals(op)) {
                return offset;
            } else {
                return -1 * offset;
            }
        }

        return 0;
    }

}
