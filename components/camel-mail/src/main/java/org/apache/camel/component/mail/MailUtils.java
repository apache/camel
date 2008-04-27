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

/**
 * Mail utility class.
 * <p>
 * Parts of the code copied from Apache ServiceMix.
 *
 * @version $Revision$
 */
public class MailUtils {

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

    // TODO: Add public method for aiding mail message logging
    
}
