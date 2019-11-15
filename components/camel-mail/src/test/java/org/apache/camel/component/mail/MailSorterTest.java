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

import java.util.Date;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;

import com.sun.mail.imap.SortTerm;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.mockito.Mockito;

import static org.mockito.Mockito.when;

/**
 * Tests mail sort util
 */
public class MailSorterTest extends CamelTestSupport {
    private static final Message[] MESSAGES = new Message[3];
    private static final Message TIE_BREAKER;

    /**
     * All possible sort terms
     */
    private static final SortTerm[] POSSIBLE_TERMS = 
        new SortTerm[] {
            SortTerm.ARRIVAL, SortTerm.CC,
            SortTerm.DATE, SortTerm.FROM,
            SortTerm.SIZE, SortTerm.TO,
            SortTerm.SUBJECT};

    static {
        try {
            MESSAGES[0] = createMessage("to1", "cc1", "from1", new Date(1), new Date(1001), 1, "subject1");
            MESSAGES[1] = createMessage("to2", "cc2", "from2", new Date(2), new Date(1002), 2, "subject2");
            MESSAGES[2] = createMessage("to3", "cc3", "from3", new Date(3), new Date(1003), 3, "subject3");
            // Message that creates a tie on all fields except for one
            TIE_BREAKER = createMessage("to3", "cc3", "from3", new Date(3), new Date(1003), 3, "subject0TieBreaker");
        } catch (MessagingException e) {
            // Rethrow as unchecked. Can not occur anyways
            throw new IllegalStateException(e);
        }
    }

    /**
     * Create a new message with the specified data
     */
    private static Message createMessage(String to, String cc, String from, Date received, Date sent, int size, String subject)
        throws MessagingException {
        final Message msg = Mockito.mock(Message.class);
        when(msg.getFrom()).thenReturn(new Address[]{new InternetAddress(from)});
        when(msg.getRecipients(Message.RecipientType.TO)).thenReturn(new Address[]{new InternetAddress(to)});
        when(msg.getRecipients(Message.RecipientType.CC)).thenReturn(new Address[]{new InternetAddress(cc)});
        when(msg.getSentDate()).thenReturn(sent);
        when(msg.getReceivedDate()).thenReturn(received);
        when(msg.getSize()).thenReturn(size);
        when(msg.getSubject()).thenReturn(subject);
        return msg;
    }

    @Test
    public void testSortMessages() throws Exception {
        Message[] expected = new Message[]{MESSAGES[0], MESSAGES[1], MESSAGES[2]};

        // Sort using all the terms. Message order should be the same no matter what term is used
        for (SortTerm term : POSSIBLE_TERMS) {
            Message[] actual = MESSAGES.clone();
            MailSorter.sortMessages(actual, new SortTerm[]{term});
            try {
                assertArrayEquals(actual, expected);
            } catch (Exception ex) {
                throw new Exception("Term: " + term.toString(), ex);
            }
        }
    }

    @Test
    public void testSortMessagesReverse() throws Exception {
        Message[] expected = new Message[]{MESSAGES[2], MESSAGES[1], MESSAGES[0]};

        // Sort using all the terms. Message order should be the same no matter what term is used
        for (SortTerm term : POSSIBLE_TERMS) {
            Message[] actual = MESSAGES.clone();
            MailSorter.sortMessages(actual, new SortTerm[]{SortTerm.REVERSE, term});
            try {
                assertArrayEquals(actual, expected);
            } catch (AssertionError ex) {
                throw new AssertionError("Term: " + term.toString(), ex);
            }
        }
    }

    @Test
    public void testSortMessagesMulti() throws Exception {
        Message[] expected = new Message[]{MESSAGES[0], MESSAGES[1], MESSAGES[2]};

        // Sort using all the terms. Message order should be the same no matter what term is used. The second term
        // should be ignored since it is already the decider.
        for (SortTerm term1 : POSSIBLE_TERMS) {
            for (SortTerm term2 : POSSIBLE_TERMS) {
                Message[] actual = MESSAGES.clone();
                MailSorter.sortMessages(actual, new SortTerm[]{term1, SortTerm.REVERSE, term2});
                try {
                    assertArrayEquals(actual, expected);
                } catch (AssertionError ex) {
                    throw new AssertionError(String.format("Terms: %s, %s", term1.toString(), term2.toString()), ex);
                }
            }

        }
    }

    @Test
    public void testSortMessagesWithTie() throws Exception {
        Message[] given = new Message[]{MESSAGES[2], TIE_BREAKER};

        // Sort according to the whole list. Only the last element breaks the tie
        Message[] actual1 = given.clone();
        MailSorter.sortMessages(actual1, POSSIBLE_TERMS);
        assertArrayEquals(actual1, new Message[]{TIE_BREAKER, MESSAGES[2]});

        // now reverse the last element (the tie breaker)
        SortTerm[] reversed = new SortTerm[POSSIBLE_TERMS.length + 1];
        System.arraycopy(POSSIBLE_TERMS, 0, reversed, 0, POSSIBLE_TERMS.length - 1);
        reversed[reversed.length - 2] = SortTerm.REVERSE;
        reversed[reversed.length - 1] = POSSIBLE_TERMS[POSSIBLE_TERMS.length - 1];
        // And check again
        Message[] actual2 = given.clone();
        MailSorter.sortMessages(actual2, reversed);
        assertArrayEquals(actual2, new Message[]{MESSAGES[2], TIE_BREAKER});
    }
}
