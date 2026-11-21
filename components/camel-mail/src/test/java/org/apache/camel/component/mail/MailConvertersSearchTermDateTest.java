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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.search.SearchTerm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The fixed-date form of the date search options keeps the seconds of the date (yyyy-MM-dd HH:mm:ss).
 */
class MailConvertersSearchTermDateTest {

    @Test
    void fromSentDateKeepsTheSeconds() throws Exception {
        SimpleSearchTerm simple = new SimpleSearchTerm();
        simple.setUnseen(false);
        simple.setFromSentDate("2026-11-18 14:30:45");
        SearchTerm term = MailConverters.toSearchTerm(simple);

        assertFalse(term.match(sentAt("2026-11-18 14:30:10")), "a mail sent before the given time");
        assertTrue(term.match(sentAt("2026-11-18 14:30:50")), "a mail sent after the given time");
    }

    @Test
    void toSentDateKeepsTheSeconds() throws Exception {
        SimpleSearchTerm simple = new SimpleSearchTerm();
        simple.setUnseen(false);
        simple.setToSentDate("2026-11-18 14:30:45");
        SearchTerm term = MailConverters.toSearchTerm(simple);

        assertTrue(term.match(sentAt("2026-11-18 14:30:10")), "a mail sent before the given time");
        assertFalse(term.match(sentAt("2026-11-18 14:30:50")), "a mail sent after the given time");
    }

    @Test
    void fromReceivedDateKeepsTheSeconds() throws Exception {
        SimpleSearchTerm simple = new SimpleSearchTerm();
        simple.setUnseen(false);
        simple.setFromReceivedDate("2026-11-18 14:30:45");
        SearchTerm term = MailConverters.toSearchTerm(simple);

        assertFalse(term.match(receivedAt("2026-11-18 14:30:10")), "a mail received before the given time");
        assertTrue(term.match(receivedAt("2026-11-18 14:30:50")), "a mail received after the given time");
    }

    @Test
    void toReceivedDateKeepsTheSeconds() throws Exception {
        SimpleSearchTerm simple = new SimpleSearchTerm();
        simple.setUnseen(false);
        simple.setToReceivedDate("2026-11-18 14:30:45");
        SearchTerm term = MailConverters.toSearchTerm(simple);

        assertTrue(term.match(receivedAt("2026-11-18 14:30:10")), "a mail received before the given time");
        assertFalse(term.match(receivedAt("2026-11-18 14:30:50")), "a mail received after the given time");
    }

    private static MimeMessage sentAt(String date) throws Exception {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        message.setSentDate(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(date));
        return message;
    }

    private static MimeMessage receivedAt(String date) throws Exception {
        Date received = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(date);
        // only a mail server sets the received date, so a MimeMessage made here has none
        return new MimeMessage(Session.getInstance(new Properties())) {
            @Override
            public Date getReceivedDate() {
                return received;
            }
        };
    }
}
