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

import java.text.DateFormat;
import java.util.Date;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;

/**
 * Mail utility class.
 * <p>
 * Parts of the code copied from Apache ServiceMix.
 *
 * @version $Revision$
 */
public final class MailUtils {

    public static final int DEFAULT_PORT_SMTP = 25;
    public static final int DEFAULT_PORT_SMTPS = 465;
    public static final int DEFAULT_PORT_POP3 = 110;
    public static final int DEFAULT_PORT_POP3S = 995;
    public static final int DEFAULT_PORT_NNTP = 119;
    public static final int DEFAULT_PORT_IMAP = 143;
    public static final int DEFAULT_PORT_IMAPS = 993;

    public static final String PROTOCOL_SMTP = "smtp";
    public static final String PROTOCOL_SMTPS = "smtps";
    public static final String PROTOCOL_POP3 = "pop3";
    public static final String PROTOCOL_POP3S = "pop3s";
    public static final String PROTOCOL_NNTP = "nntp";
    public static final String PROTOCOL_IMAP = "imap";
    public static final String PROTOCOL_IMAPS = "imaps";

    private MailUtils() {
    }

    /**
     * Returns the default port for a given protocol.
     * <p>
     * If a protocol could not successfully be determined the default port number for SMTP protocol is returned.
     *
     * @param protocol the protocol
     * @return the default port
     */
    public static int getDefaultPortForProtocol(final String protocol) {
        int port = DEFAULT_PORT_SMTP;

        if (protocol != null) {
            if (protocol.equalsIgnoreCase(PROTOCOL_IMAP)) {
                port = DEFAULT_PORT_IMAP;
            } else if (protocol.equalsIgnoreCase(PROTOCOL_IMAPS)) {
                port = DEFAULT_PORT_IMAPS;
            } else if (protocol.equalsIgnoreCase(PROTOCOL_NNTP)) {
                port = DEFAULT_PORT_NNTP;
            } else if (protocol.equalsIgnoreCase(PROTOCOL_POP3)) {
                port = DEFAULT_PORT_POP3;
            } else if (protocol.equalsIgnoreCase(PROTOCOL_POP3S)) {
                port = DEFAULT_PORT_POP3S;
            } else if (protocol.equalsIgnoreCase(PROTOCOL_SMTP)) {
                port = DEFAULT_PORT_SMTP;
            } else if (protocol.equalsIgnoreCase(PROTOCOL_SMTPS)) {
                port = DEFAULT_PORT_SMTPS;
            } else {
                port = DEFAULT_PORT_SMTP;
            }
        }

        return port;
    }

    /**
     * Gets a log dump of the given message that can be used for tracing etc.
     *
     * @param message the Mail message
     * @return a log string with important fields dumped
     */
    public static String dumpMessage(Message message) {
        try {
            StringBuilder sb = new StringBuilder();

            int number = message.getMessageNumber();
            sb.append("messageNumber=[").append(number).append("]");

            Address[] from = message.getFrom();
            if (from != null) {
                for (Address adr : from) {
                    sb.append(", from=[").append(adr).append("]");
                }
            }

            Address[] to = message.getRecipients(Message.RecipientType.TO);
            if (to != null) {
                for (Address adr : to) {
                    sb.append(", to=[").append(adr).append("]");
                }
            }

            String subject = message.getSubject();
            if (subject != null) {
                sb.append(", subject=[").append(subject).append("]");
            }

            Date sentDate = message.getSentDate();
            if (sentDate != null) {
                sb.append(", sentDate=[").append(DateFormat.getDateTimeInstance().format(sentDate)).append("]");
            }

            Date receivedDate = message.getReceivedDate();
            if (receivedDate != null) {
                sb.append(", receivedDate=[").append(DateFormat.getDateTimeInstance().format(receivedDate)).append("]");
            }

            return sb.toString();
        } catch (MessagingException e) {
            // ignore the error and just return tostring 
            return message.toString();
        }

    }

}
