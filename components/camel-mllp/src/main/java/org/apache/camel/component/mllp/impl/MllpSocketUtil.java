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
package org.apache.camel.component.mllp.impl;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.mllp.MllpEndpoint.END_OF_BLOCK;
import static org.apache.camel.component.mllp.MllpEndpoint.END_OF_DATA;
import static org.apache.camel.component.mllp.MllpEndpoint.START_OF_BLOCK;

public final class MllpSocketUtil {

    private static final Logger LOG = LoggerFactory.getLogger(MllpSocketUtil.class);

    private MllpSocketUtil() {
    }

    public static void setSoTimeout(Socket socket, int timeout, Logger logger, String reasonMessage) {
        if (logger != null && logger.isDebugEnabled()) {
            final String format = "Setting SO_TIMEOUT to {} for connection {}";
            if (reasonMessage != null && !reasonMessage.isEmpty()) {
                logger.debug(format + "  Reason: {}", timeout, getAddressString(socket), reasonMessage);
            } else {
                logger.debug(format, timeout, getAddressString(socket));
            }
        }
        try {
            socket.setSoTimeout(timeout);
        } catch (SocketException socketEx) {
            if (logger != null) {
                final String format = "Ignoring SocketException encountered setting SO_TIMEOUT to %d for connection %s.";
                if (reasonMessage != null && !reasonMessage.isEmpty()) {
                    logger.warn(String.format(format + "  Reason: %s", timeout, getAddressString(socket), reasonMessage), socketEx);
                } else {
                    logger.warn(String.format(format, timeout, getAddressString(socket)), socketEx);
                }
            }
        }
    }

    public static void close(Socket socket, Logger logger, String reasonMessage) {
        if (socket != null && socket.isConnected() && !socket.isClosed()) {
            final String format = "Closing connection {}";

            String address = getAddressString(socket);

            if (logger != null) {
                if (reasonMessage != null && !reasonMessage.isEmpty()) {
                    logger.warn(format + ".  Reason: {}", address, reasonMessage);
                } else {
                    logger.warn(format, address);
                }
            }

            if (!socket.isInputShutdown()) {
                try {
                    socket.shutdownInput();
                } catch (Exception ex) {
                    String logMessage = String.format("Ignoring Exception encountered shutting down the input stream on the client socket %s", address);
                    if (logger != null) {
                        logger.warn(logMessage, ex);
                    } else {
                        LOG.warn(logMessage, ex);
                    }
                }
            }

            if (!socket.isOutputShutdown()) {
                try {
                    socket.shutdownOutput();
                } catch (Exception ex) {
                    String logMessage = String.format("Ignoring Exception encountered shutting down the output stream on the client socket %s", address);
                    if (logger != null) {
                        logger.warn(logMessage, ex);
                    } else {
                        LOG.warn(logMessage, ex);
                    }
                }
            }

            try {
                socket.close();
            } catch (IOException ioEx) {
                String logMessage = String.format("Ignoring IOException encountered while closing connection %s", address);
                if (logger != null) {
                    logger.warn(logMessage, ioEx);
                } else {
                    LOG.warn(logMessage, ioEx);
                }
            }
        }
    }

    public static void reset(Socket socket, Logger logger, String reasonMessage) {
        if (socket != null && socket.isConnected() && !socket.isClosed()) {
            final String format = "Resetting connection {}";

            String address = getAddressString(socket);

            if (logger != null) {
                if (reasonMessage != null && !reasonMessage.isEmpty()) {
                    logger.warn(format + ".  Reason: {}", address, reasonMessage);
                } else {
                    logger.warn(format, address);
                }
            }

            try {
                socket.setSoLinger(true, 0);
            } catch (SocketException socketEx) {
                String logMessage = String.format("Ignoring SocketException encountered setting SO_LINGER in preparation for resetting connection %s", address);
                if (logger != null) {
                    logger.warn(logMessage, socketEx);
                } else {
                    LOG.warn(logMessage, socketEx);
                }
            }

            try {
                socket.close();
            } catch (IOException ioEx) {
                String logMessage = String.format("Ignoring IOException encountered while resetting connection %s", address);
                if (logger != null) {
                    logger.warn(logMessage, ioEx);
                } else {
                    LOG.warn(logMessage, ioEx);
                }
            }
        }
    }

    public static String getAddressString(Socket socket) {
        String localAddressString = "null";
        String remoteAddressString = "null";

        if (socket != null) {
            SocketAddress localSocketAddress = socket.getLocalSocketAddress();
            if (localSocketAddress != null) {
                localAddressString = localSocketAddress.toString();
            }

            SocketAddress remoteSocketAddress = socket.getRemoteSocketAddress();
            if (remoteSocketAddress != null) {
                remoteAddressString = remoteSocketAddress.toString();
            }
        }

        return String.format("%s -> %s", localAddressString, remoteAddressString);
    }

    public static int findStartOfBlock(byte[] payload) {
        if (payload != null) {
            return findStartOfBlock(payload, payload.length);
        }

        return -1;
    }

    /**
     * Find the beginning of the HL7 Payload
     * <p>
     * Searches the payload from the beginning, looking for the START_OF_BLOCK character.
     *
     * @param payload the payload to check
     * @param length  the current valid length of the receive buffer
     * @return the index of the START_OF_BLOCK, or -1 if not found
     */
    public static int findStartOfBlock(byte[] payload, int length) {
        if (payload != null && length >= 0) {
            for (int i = 0; i < Math.min(length, payload.length); ++i) {
                if (payload[i] == START_OF_BLOCK) {
                    return i;
                }
            }
        }

        return -1;
    }

    public static int findEndOfMessage(byte[] payload) {
        if (payload != null) {
            return findEndOfMessage(payload, payload.length);
        }

        return -1;
    }

    /**
     * Find the end of the HL7 Payload
     * <p>
     * Searches the payload from the end, looking for the [END_OF_BLOCK, END_OF_DATA] characters.
     *
     * @param payload the payload to check
     * @param length  the current valid length of the receive buffer
     * @return the index of the END_OF_BLOCK character that terminates the message, or -1 if not found
     */
    public static int findEndOfMessage(byte[] payload, int length) {
        if (payload != null && length >= 0) {
            for (int i = Math.min(length, payload.length) - 1; i > 0; --i) {
                if (payload[i] == END_OF_DATA) {
                    if (payload[i - 1] == END_OF_BLOCK) {
                        return i - 1;
                    }
                }
            }
        }

        return -1;
    }

}
