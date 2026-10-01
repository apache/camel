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
import java.util.List;

import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;

import org.eclipse.angus.mail.imap.SortTerm;

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
     * capability. See RFC 5256 Does not support complex sorting like in the RFC (with Base Subject or other similar
     * stuff), just simple comparisons.
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
     * @param  sortTerm Input list
     * @return          Sort terms list including if the respective sort should be sorted in descending order
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
     * @param messages                Messages to sort. Are sorted in place
     * @param sortTermsWithDescending Sort terms list including if the respective sort should be sorted in descending
     *                                order
     */
    private static void sortMessages(Message[] messages, final List<SortTermWithDescending> sortTermsWithDescending) {
        Arrays.sort(messages, (Message m1, Message m2) -> {
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
        });
    }

    /**
     * Compare the value of the property of the two messages.
     *
     * @param  msg1                            Message 1
     * @param  msg2                            Message 2
     * @param  property                        Property to compare
     * @return                                 msg1.property.compareTo(msg2.property)
     * @throws jakarta.mail.MessagingException If message data could not be read.
     */
    private static int compareMessageProperty(Message msg1, Message msg2, SortTerm property) throws MessagingException {
        // Every value read here is optional in RFC 5322 or may be absent on the server, so each comparison
        // must tolerate a missing value. A message legitimately lacking the sorted-on property previously
        // threw from inside the comparator and aborted the whole poll, on every subsequent poll.
        if (property.equals(SortTerm.TO)) {
            return compareNullable(firstAddress(msg1.getRecipients(Message.RecipientType.TO)),
                    firstAddress(msg2.getRecipients(Message.RecipientType.TO)));
        } else if (property.equals(SortTerm.CC)) {
            return compareNullable(firstAddress(msg1.getRecipients(Message.RecipientType.CC)),
                    firstAddress(msg2.getRecipients(Message.RecipientType.CC)));
        } else if (property.equals(SortTerm.FROM)) {
            return compareNullable(firstAddress(msg1.getFrom()), firstAddress(msg2.getFrom()));
        } else if (property.equals(SortTerm.ARRIVAL)) {
            return compareNullable(msg1.getReceivedDate(), msg2.getReceivedDate());
        } else if (property.equals(SortTerm.DATE)) {
            return compareNullable(msg1.getSentDate(), msg2.getSentDate());
        } else if (property.equals(SortTerm.SIZE)) {
            return Integer.compare(msg1.getSize(), msg2.getSize());
        } else if (property.equals(SortTerm.SUBJECT)) {
            return compareNullable(msg1.getSubject(), msg2.getSubject());
        }
        throw new IllegalArgumentException(String.format("Unknown sort term: %s", property.toString()));
    }

    /**
     * Returns the address of the first entry, or null when the message carries none.
     */
    private static String firstAddress(Address[] addresses) {
        if (addresses == null || addresses.length == 0) {
            return null;
        }
        if (addresses[0] instanceof InternetAddress internetAddress) {
            return internetAddress.getAddress();
        }
        return addresses[0].toString();
    }

    /**
     * Compares two optional values, ordering a missing one first. Sorting must not depend on every message carrying the
     * sorted-on property.
     */
    private static <T extends Comparable<T>> int compareNullable(T value1, T value2) {
        if (value1 == null && value2 == null) {
            return 0;
        }
        if (value1 == null) {
            return -1;
        }
        if (value2 == null) {
            return 1;
        }
        return value1.compareTo(value2);
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
