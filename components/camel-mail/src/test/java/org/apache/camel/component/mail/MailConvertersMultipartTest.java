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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit6.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Converts mails read from their raw form, as a mail consumer gives them, to String.
 */
public class MailConvertersMultipartTest extends CamelTestSupport {

    private final Session session = Session.getInstance(new Properties());

    @Test
    public void testMultipartWithoutTextDoesNotBreakLaterConversions() throws Exception {
        MimeMultipart pdfOnly = multipartOf("""
                --b1
                Content-Type: application/pdf; name=doc.pdf
                Content-Disposition: attachment; filename=doc.pdf
                Content-Transfer-Encoding: base64

                JVBERi0xLjQK
                --b1--
                """);
        MimeMultipart withText = multipartOf("""
                --b1
                Content-Type: text/plain; charset=us-ascii

                Hello with attachment
                --b1
                Content-Type: application/pdf; name=doc.pdf
                Content-Disposition: attachment; filename=doc.pdf
                Content-Transfer-Encoding: base64

                JVBERi0xLjQK
                --b1--
                """);

        Exchange exchange = new DefaultExchange(context);
        // a multipart without text has no text
        assertNull(context.getTypeConverter().convertTo(String.class, exchange, pdfOnly));
        // and the next multipart still converts to its text
        assertEquals("Hello with attachment", context.getTypeConverter().convertTo(String.class, exchange, withText).trim());
    }

    @Test
    public void testMultipartWithStreamTextPart() throws Exception {
        MimeMultipart csv = multipartOf("""
                --b1
                Content-Type: text/csv; charset=ISO-8859-1
                Content-Transfer-Encoding: quoted-printable

                name,city
                J=FCrgen,K=F8benhavn
                --b1--
                """);

        assertEquals("name,city\r\nJ\u00fcrgen,K\u00f8benhavn", MailConverters.toString(csv).trim());
    }

    @Test
    public void testMessageWithStreamTextContent() throws Exception {
        MimeMessage message = messageOf("""
                Subject: Calendar
                Content-Type: text/calendar; charset=UTF-8
                Content-Transfer-Encoding: 8bit

                BEGIN:VCALENDAR
                SUMMARY:Gr\u00fc\u00dfe
                END:VCALENDAR
                """);
        assertIsInstanceOf(InputStream.class, message.getContent());

        assertEquals("BEGIN:VCALENDAR\r\nSUMMARY:Gr\u00fc\u00dfe\r\nEND:VCALENDAR", MailConverters.toString(message).trim());
    }

    private MimeMultipart multipartOf(String parts) throws Exception {
        MimeMessage message = messageOf("Subject: Test\nContent-Type: multipart/mixed; boundary=b1\n\n" + parts);
        return assertIsInstanceOf(MimeMultipart.class, message.getContent());
    }

    private MimeMessage messageOf(String raw) throws Exception {
        byte[] data = raw.replace("\n", "\r\n").getBytes(StandardCharsets.UTF_8);
        return new MimeMessage(session, new ByteArrayInputStream(data));
    }
}
