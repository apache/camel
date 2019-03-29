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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;

import com.sun.mail.imap.SortTerm;

/**
 * Utility class for sorting of mail messages
 */
public final class MailSorter {
    /**
     * No instances
     */
    private MailSorter() {
    }

    /**
     * Sort the messages. This emulates sorting the messages on the server if the server doesn't have the sorting
     * capability. See RFC 5256
     * Does not support complex sorting like in the RFC (with Base Subject or other similar stuff), just simple
     * comparisons.
     *
     * @param messages Messages to sort. Are sorted in place
     * @param sortTerm Sort term
     */
    public static void sortMessages(Message[] messages, final SortTerm[] sortTerm) {
        final List<SortTermWithDescending> sortTermsWithDescending = getSortTermsWithDescending(sortTerm);
        sortMessages(messages, sortTermsWithDescending);
    }

    /**
     * Compute the potentially descending sort terms from the input list
     *
     * @param sortTerm Input list
     * @return Sort terms list including if the respective sort should be sorted in descending order
     */
    private static List<SortTermWithDescending> getSortTermsWithDescending(SortTerm[] sortTerm) {
        // List of reversable sort terms. If the boolean is true the respective sort term is descending
        final List<SortTermWithDescending> sortTermsWithDescending = new ArrayList<>(sortTerm.length);
        // Descending next item in input because the last item was a "descending"
        boolean descendingNext = false;
        for (SortTerm term : sortTerm) {
            if (term.equals(SortTerm.REVERSE)) {
                if (descendingNext) {
                    throw new IllegalArgumentException("Double reverse in sort term is not allowed");
                }
                descendingNext = true;
            } else {
                sortTermsWithDescending.add(new SortTermWithDescending(term, descendingNext));
                descendingNext = false;
            }
        }
        return sortTermsWithDescending;
    }

    /**
     * Sort messages using the list of properties
     *
     * @param messages             Messages to sort. Are sorted in place
     * @param sortTermsWithDescending Sort terms list including if the respective sort should be sorted in descending order
     */
    private static void sortMessages(Message[] messages, final List<SortTermWithDescending> sortTermsWithDescending) {
        Arrays.sort(messages, new Comparator<Message>() {
            @Override
            public int compare(Message m1, Message m2) {
                try {
                    for (SortTermWithDescending reversableTerm : sortTermsWithDescending) {
                        int comparison = compareMessageProperty(m1, m2, reversableTerm.getTerm());
                        // Descending
                        if (reversableTerm.isDescending()) {
                            comparison = -comparison;
                        }
                        // Abort on first non-equal
                        if (comparison != 0) {
                            return comparison;
                        }
                    }
                    // Equal
                    return 0;
                } catch (MessagingException e) {
                    throw new IllegalArgumentException(e);
                }
            }
        });
    }

    /**
     * Compare the value of the property of the two messages.
     *
     * @param msg1     Message 1
     * @param msg2     Message 2
     * @param property Property to compare
     * @return msg1.property.compareTo(msg2.property)
     * @throws javax.mail.MessagingException If message data could not be read.
     */
    private static int compareMessageProperty(Message msg1, Message msg2, SortTerm property) throws MessagingException {
        if (property.equals(SortTerm.TO)) {
            InternetAddress addr1 = (InternetAddress) msg1.getRecipients(Message.RecipientType.TO)[0];
            InternetAddress addr2 = (InternetAddress) msg2.getRecipients(Message.RecipientType.TO)[0];
            return addr1.getAddress().compareTo(addr2.getAddress());
        } else if (property.equals(SortTerm.CC)) {
            InternetAddress addr1 = (InternetAddress) msg1.getRecipients(Message.RecipientType.CC)[0];
            InternetAddress addr2 = (InternetAddress) msg2.getRecipients(Message.RecipientType.CC)[0];
            return addr1.getAddress().compareTo(addr2.getAddress());
        } else if (property.equals(SortTerm.FROM)) {
            InternetAddress addr1 = (InternetAddress) msg1.getFrom()[0];
            InternetAddress addr2 = (InternetAddress) msg2.getFrom()[0];
            return addr1.getAddress().compareTo(addr2.getAddress());
        } else if (property.equals(SortTerm.ARRIVAL)) {
            Date arr1 = msg1.getReceivedDate();
            Date arr2 = msg2.getReceivedDate();
            return arr1.compareTo(arr2);
        } else if (property.equals(SortTerm.DATE)) {
            Date sent1 = msg1.getSentDate();
            Date sent2 = msg2.getSentDate();
            return sent1.compareTo(sent2);
        } else if (property.equals(SortTerm.SIZE)) {
            int size1 = msg1.getSize();
            int size2 = msg2.getSize();
            return Integer.compare(size1, size2);
        } else if (property.equals(SortTerm.SUBJECT)) {
            String sub1 = msg1.getSubject();
            String sub2 = msg2.getSubject();
            return sub1.compareTo(sub2);
        }
        throw new IllegalArgumentException(String.format("Unknown sort term: %s", property.toString()));
    }

    /**
     * A sort term with a bit indicating if sorting should be descending for this term
     */
    private static final class SortTermWithDescending {
        private SortTerm term;
        private boolean descending;

        private SortTermWithDescending(SortTerm term, boolean descending) {
            this.term = term;
            this.descending = descending;
        }

        /**
         * @return Actual search term
         */
        public SortTerm getTerm() {
            return term;
        }

        /**
         * @return true if sorting should be descending, false if it should be ascending
         */
        public boolean isDescending() {
            return descending;
        }
    }
}
