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

package org.apache.camel.component.mllp.internal;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.Route;
import org.apache.camel.component.mllp.MllpAcknowledgementDeliveryException;
import org.apache.camel.component.mllp.MllpConstants;
import org.apache.camel.component.mllp.MllpInvalidAcknowledgementException;
import org.apache.camel.component.mllp.MllpInvalidMessageException;
import org.apache.camel.component.mllp.MllpProtocolConstants;
import org.apache.camel.component.mllp.MllpReceiveException;
import org.apache.camel.component.mllp.MllpSocketException;
import org.apache.camel.component.mllp.MllpTcpServerConsumer;
import org.apache.camel.converter.IOConverter;
import org.apache.camel.impl.MDCUnitOfWork;
import org.apache.camel.processor.mllp.Hl7AcknowledgementGenerationException;
import org.apache.camel.util.IOHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Runnable to read the Socket
 */
public class TcpSocketConsumerRunnable implements Runnable {
    final Socket clientSocket;
    final MllpSocketBuffer mllpBuffer;

    Logger log = LoggerFactory.getLogger(this.getClass());
    MllpTcpServerConsumer consumer;
    boolean running;

    private final String localAddress;
    private final String remoteAddress;
    private final String combinedAddress;

    public TcpSocketConsumerRunnable(MllpTcpServerConsumer consumer, Socket clientSocket) {
        this.consumer = consumer;
        // this.setName(createThreadName(clientSocket));
        this.clientSocket = clientSocket;

        SocketAddress localSocketAddress = clientSocket.getLocalSocketAddress();
        if (localSocketAddress != null) {
            localAddress = localSocketAddress.toString();
        } else {
            localAddress = null;
        }

        SocketAddress remoteSocketAddress = clientSocket.getRemoteSocketAddress();
        if (remoteSocketAddress != null) {
            remoteAddress = remoteSocketAddress.toString();
        } else {
            remoteAddress = null;
        }

        combinedAddress = MllpSocketBuffer.formatAddressString(remoteSocketAddress, localSocketAddress);


        try {
            if (consumer.getConfiguration().hasKeepAlive()) {
                this.clientSocket.setKeepAlive(consumer.getConfiguration().getKeepAlive());
            }
            if (consumer.getConfiguration().hasTcpNoDelay()) {
                this.clientSocket.setTcpNoDelay(consumer.getConfiguration().getTcpNoDelay());
            }
            if (consumer.getConfiguration().hasReceiveBufferSize()) {
                this.clientSocket.setReceiveBufferSize(consumer.getConfiguration().getReceiveBufferSize());
            }
            if (consumer.getConfiguration().hasSendBufferSize()) {
                this.clientSocket.setSendBufferSize(consumer.getConfiguration().getSendBufferSize());
            }

            this.clientSocket.setSoLinger(false, -1);

            // Initial Read Timeout
            this.clientSocket.setSoTimeout(consumer.getConfiguration().getReceiveTimeout());
        } catch (IOException initializationException) {
            throw new IllegalStateException("Failed to initialize " + this.getClass().getSimpleName(), initializationException);
        }

        mllpBuffer = new MllpSocketBuffer(consumer.getEndpoint());
    }

    /**
     * derive a thread name from the class name, the component URI and the connection information
     * <p/>
     * The String will in the format <class name>[endpoint key] - [local socket address] -> [remote socket address]
     *
     * @return the thread name
     */
    String createThreadName(Socket socket) {
        // Get the URI without options
        String fullEndpointKey = consumer.getEndpoint().getEndpointKey();
        String endpointKey;
        if (fullEndpointKey.contains("?")) {
            endpointKey = fullEndpointKey.substring(0, fullEndpointKey.indexOf('?'));
        } else {
            endpointKey = fullEndpointKey;
        }

        // Now put it all together
        return String.format("%s[%s] - %s", this.getClass().getSimpleName(), endpointKey, combinedAddress);
    }

    private void sendAcknowledgement(byte[] originalHl7MessageBytes, Exchange exchange) {
        log.trace("entering sendAcknowledgement(byte[], Exchange)");

        consumer.getEndpoint().checkBeforeSendProperties(exchange, clientSocket, log);

        // Find the acknowledgement body
        byte[] acknowledgementMessageBytes = exchange.getProperty(MllpConstants.MLLP_ACKNOWLEDGEMENT, byte[].class);
        if (acknowledgementMessageBytes == null) {
            acknowledgementMessageBytes = exchange.getProperty(MllpConstants.MLLP_ACKNOWLEDGEMENT_STRING, byte[].class);
        }

        String acknowledgementMessageType = null;
        if (null == acknowledgementMessageBytes) {

            boolean autoAck = exchange.getProperty(MllpConstants.MLLP_AUTO_ACKNOWLEDGE, true, boolean.class);
            if (!autoAck) {
                Object acknowledgementBytesProperty = exchange.getProperty(MllpConstants.MLLP_ACKNOWLEDGEMENT);
                Object acknowledgementStringProperty = exchange.getProperty(MllpConstants.MLLP_ACKNOWLEDGEMENT_STRING);
                if (acknowledgementBytesProperty == null && acknowledgementStringProperty == null) {
                    final String exceptionMessage = "Automatic Acknowledgement is disabled and the "
                        + MllpConstants.MLLP_ACKNOWLEDGEMENT + " and " + MllpConstants.MLLP_ACKNOWLEDGEMENT_STRING + " exchange properties are null";
                    exchange.setException(new MllpInvalidAcknowledgementException(exceptionMessage, originalHl7MessageBytes, acknowledgementMessageBytes));
                } else {
                    final String exceptionMessage = "Automatic Acknowledgement is disabled and neither the "
                        + MllpConstants.MLLP_ACKNOWLEDGEMENT + "(type = " + acknowledgementBytesProperty.getClass().getSimpleName() + ") nor  the"
                        + MllpConstants.MLLP_ACKNOWLEDGEMENT_STRING + "(type = " + acknowledgementBytesProperty.getClass().getSimpleName() + ") exchange properties can be converted to byte[]";
                    exchange.setException(new MllpInvalidAcknowledgementException(exceptionMessage, originalHl7MessageBytes, acknowledgementMessageBytes));
                }
            } else {
                String acknowledgmentTypeProperty = exchange.getProperty(MllpConstants.MLLP_ACKNOWLEDGEMENT_TYPE, String.class);
                try {
                    if (null == acknowledgmentTypeProperty) {
                        if (null == exchange.getException()) {
                            acknowledgementMessageType = "AA";
                        } else {
                            acknowledgementMessageType = "AE";
                        }
                    } else {
                        switch (acknowledgmentTypeProperty) {
                        case "AA":
                            acknowledgementMessageType = "AA";
                            break;
                        case "AE":
                            acknowledgementMessageType = "AE";
                            break;
                        case "AR":
                            acknowledgementMessageType = "AR";
                            break;
                        default:
                            exchange.setException(new Hl7AcknowledgementGenerationException("Unsupported acknowledgment type: " + acknowledgmentTypeProperty));
                            return;
                        }
                    }

                    Hl7Util.generateAcknowledgementPayload(mllpBuffer, originalHl7MessageBytes, acknowledgementMessageType);

                } catch (Hl7AcknowledgementGenerationException ackGenerationException) {
                    exchange.setProperty(MllpConstants.MLLP_ACKNOWLEDGEMENT_EXCEPTION, ackGenerationException);
                    exchange.setException(ackGenerationException);
                }
            }
        } else {
            mllpBuffer.setEnvelopedMessage(acknowledgementMessageBytes);

            final byte bM = 77;
            final byte bS = 83;
            final byte bA = 65;
            final byte bE = 69;
            final byte bR = 82;

            final byte fieldSeparator = originalHl7MessageBytes[3];
            // Acknowledgment is specified in exchange property - determine the acknowledgement type
            for (int i = 0; i < originalHl7MessageBytes.length; ++i) {
                if (MllpProtocolConstants.SEGMENT_DELIMITER == i) {
                    if (i + 7 < originalHl7MessageBytes.length // Make sure we don't run off the end of the message
                        && bM == originalHl7MessageBytes[i + 1] && bS == originalHl7MessageBytes[i + 2]
                        && bA == originalHl7MessageBytes[i + 3] && fieldSeparator == originalHl7MessageBytes[i + 4]) {
                        if (fieldSeparator != originalHl7MessageBytes[i + 7]) {
                            log.warn("MSA-1 is longer than 2-bytes - ignoring trailing bytes");
                        }
                        // Found MSA - pull acknowledgement bytes
                        byte[] acknowledgmentTypeBytes = new byte[2];
                        acknowledgmentTypeBytes[0] = originalHl7MessageBytes[i + 5];
                        acknowledgmentTypeBytes[1] = originalHl7MessageBytes[i + 6];
                        try {
                            acknowledgementMessageType = IOConverter.toString(acknowledgmentTypeBytes, exchange);
                        } catch (IOException ioEx) {
                            throw new RuntimeException("Failed to convert acknowledgement message to string", ioEx);
                        }

                        // Verify it's a valid acknowledgement code
                        if (bA != acknowledgmentTypeBytes[0]) {
                            switch (acknowledgementMessageBytes[1]) {
                            case bA:
                            case bR:
                            case bE:
                                break;
                            default:
                                log.warn("Invalid acknowledgement type [" + acknowledgementMessageType + "] found in message - should be AA, AE or AR");
                            }
                        }

                        // if the MLLP_ACKNOWLEDGEMENT_TYPE property is set on the exchange, make sure it matches
                        String acknowledgementTypeProperty = exchange.getProperty(MllpConstants.MLLP_ACKNOWLEDGEMENT_TYPE, String.class);
                        if (null != acknowledgementTypeProperty && !acknowledgementTypeProperty.equals(acknowledgementMessageType)) {
                            log.warn("Acknowledgement type found in message [" + acknowledgementMessageType + "] does not match "
                                + MllpConstants.MLLP_ACKNOWLEDGEMENT_TYPE + " exchange property value [" + acknowledgementTypeProperty + "] - using value found in message");
                        }
                    }
                }
            }
        }

        Message message = exchange.hasOut() ? exchange.getOut() : exchange.getIn();
        if (acknowledgementMessageType != null && !acknowledgementMessageType.isEmpty()) {
            message.setHeader(MllpConstants.MLLP_ACKNOWLEDGEMENT_TYPE, acknowledgementMessageType);
        }

        if (mllpBuffer.hasCompleteEnvelope()) {
            // The mllpBuffer will be used if bufferWrites is set or if auto acknowledgement is used
            message.setHeader(MllpConstants.MLLP_ACKNOWLEDGEMENT, mllpBuffer.toMllpPayload());
            message.setHeader(MllpConstants.MLLP_ACKNOWLEDGEMENT_STRING, mllpBuffer.toHl7String(IOHelper.getCharsetName(exchange, false)));

            // Send the acknowledgement
            if (log.isDebugEnabled()) {
                log.debug("Sending Acknowledgement: {}", mllpBuffer.toPrintFriendlyHl7String());
            }

            try {
                mllpBuffer.writeTo(clientSocket);
            } catch (MllpSocketException acknowledgementDeliveryEx) {
                Exception exchangeEx = new MllpAcknowledgementDeliveryException("Failure delivering acknowledgment", originalHl7MessageBytes, acknowledgementMessageBytes, acknowledgementDeliveryEx);
                exchange.setProperty(MllpConstants.MLLP_ACKNOWLEDGEMENT_EXCEPTION, acknowledgementDeliveryEx);
                exchange.setException(exchangeEx);
            } finally {
                mllpBuffer.reset();
            }
        } else if (acknowledgementMessageBytes != null && acknowledgementMessageBytes.length > 0) {
            message.setHeader(MllpConstants.MLLP_ACKNOWLEDGEMENT, acknowledgementMessageBytes);
            String acknowledgementMessageString = "";
            String exchangeCharset = IOHelper.getCharsetName(exchange, false);
            if (exchangeCharset != null && !exchangeCharset.isEmpty()) {
                try {
                    acknowledgementMessageString = new String(acknowledgementMessageBytes, exchangeCharset);
                } catch (UnsupportedEncodingException e) {
                    log.warn("Failed to covert acknowledgment to string using {} charset - falling back to default charset {}", exchange, MllpProtocolConstants.DEFAULT_CHARSET);
                    acknowledgementMessageString = new String(acknowledgementMessageBytes, MllpProtocolConstants.DEFAULT_CHARSET);
                }
            } else {
                acknowledgementMessageString = new String(acknowledgementMessageBytes, MllpProtocolConstants.DEFAULT_CHARSET);
            }
            message.setHeader(MllpConstants.MLLP_ACKNOWLEDGEMENT_STRING, acknowledgementMessageString);

            // Send the acknowledgement
            if (log.isDebugEnabled()) {
                log.debug("Sending Acknowledgement: {}", Hl7Util.convertToPrintFriendlyString(acknowledgementMessageBytes));
            }

            try {
                mllpBuffer.setEnvelopedMessage(acknowledgementMessageBytes);
                mllpBuffer.writeTo(clientSocket);
            } catch (MllpSocketException acknowledgementDeliveryEx) {
                Exception exchangeEx = new MllpAcknowledgementDeliveryException("Failure delivering acknowledgment", originalHl7MessageBytes, acknowledgementMessageBytes, acknowledgementDeliveryEx);
                exchange.setProperty(MllpConstants.MLLP_ACKNOWLEDGEMENT_EXCEPTION, acknowledgementDeliveryEx);
                exchange.setException(exchangeEx);
            }
        }

        consumer.getEndpoint().checkAfterSendProperties(exchange, clientSocket, log);
    }

    private void populateHl7DataHeaders(Exchange exchange, Message message, byte[] hl7MessageBytes) {
        if (exchange != null && exchange.getException() == null) {
            if (hl7MessageBytes == null || hl7MessageBytes.length < 8) {
                // Not enough data to populate anything - just return
                return;
            }
            // Find the end of the MSH and indexes of the fields in the MSH to populate message headers
            final byte fieldSeparator = hl7MessageBytes[3];
            int endOfMSH = -1;
            List<Integer> fieldSeparatorIndexes = new ArrayList<>(10);  // We should have at least 10 fields

            for (int i = 0; i < hl7MessageBytes.length; ++i) {
                if (fieldSeparator == hl7MessageBytes[i]) {
                    fieldSeparatorIndexes.add(i);
                } else if (MllpProtocolConstants.SEGMENT_DELIMITER == hl7MessageBytes[i]) {
                    // If the MSH Segment doesn't have a trailing field separator, add one so the field can be extracted into a header
                    if (fieldSeparator != hl7MessageBytes[i - 1]) {
                        fieldSeparatorIndexes.add(i);
                    }
                    endOfMSH = i;
                    break;
                }
            }

            if (-1 == endOfMSH) {
                // TODO:  May want to throw some sort of an Exception here
                log.error("Population of message headers failed - unable to find the end of the MSH segment");
            } else if (consumer.getConfiguration().isHl7Headers()) {
                log.debug("Populating the HL7 message headers");
                Charset charset = Charset.forName(IOHelper.getCharsetName(exchange));

                for (int i = 2; i < fieldSeparatorIndexes.size(); ++i) {
                    int startingFieldSeparatorIndex = fieldSeparatorIndexes.get(i - 1);
                    int endingFieldSeparatorIndex = fieldSeparatorIndexes.get(i);

                    // Only populate the header if there's data in the HL7 field
                    if (endingFieldSeparatorIndex - startingFieldSeparatorIndex > 1) {
                        String headerName = null;
                        switch (i) {
                        case 2: // MSH-3
                            headerName = MllpConstants.MLLP_SENDING_APPLICATION;
                            break;
                        case 3: // MSH-4
                            headerName = MllpConstants.MLLP_SENDING_FACILITY;
                            break;
                        case 4: // MSH-5
                            headerName = MllpConstants.MLLP_RECEIVING_APPLICATION;
                            break;
                        case 5: // MSH-6
                            headerName = MllpConstants.MLLP_RECEIVING_FACILITY;
                            break;
                        case 6: // MSH-7
                            headerName = MllpConstants.MLLP_TIMESTAMP;
                            break;
                        case 7: // MSH-8
                            headerName = MllpConstants.MLLP_SECURITY;
                            break;
                        case 8: // MSH-9
                            headerName = MllpConstants.MLLP_MESSAGE_TYPE;
                            break;
                        case 9: // MSH-10
                            headerName = MllpConstants.MLLP_MESSAGE_CONTROL;
                            break;
                        case 10: // MSH-11
                            headerName = MllpConstants.MLLP_PROCESSING_ID;
                            break;
                        case 11: // MSH-12
                            headerName = MllpConstants.MLLP_VERSION_ID;
                            break;
                        case 17: // MSH-18
                            headerName = MllpConstants.MLLP_CHARSET;
                            break;
                        default:
                            // Not processing this field
                            continue;
                        }

                        String headerValue = new String(hl7MessageBytes, startingFieldSeparatorIndex + 1,
                            endingFieldSeparatorIndex - startingFieldSeparatorIndex - 1,
                            charset);
                        message.setHeader(headerName, headerValue);

                        // For MSH-9, set a couple more headers
                        if (i == 8) {
                            // final byte componentSeparator = hl7MessageBytes[4];
                            String componentSeparator = new String(hl7MessageBytes, 4, 1, charset);
                            String[] components = headerValue.split(String.format("\\Q%s\\E", componentSeparator), 3);
                            message.setHeader(MllpConstants.MLLP_EVENT_TYPE, components[0]);
                            if (2 <= components.length) {
                                message.setHeader(MllpConstants.MLLP_TRIGGER_EVENT, components[1]);
                            }
                        }
                    }
                }
            } else {
                log.trace("HL7 Message headers disabled");
            }
        }
    }


    void processMessage(byte[] hl7MessageBytes) {
        consumer.getEndpoint().updateLastConnectionActivityTicks();

        // Send the message on to Camel for processing and wait for the response
        log.debug("Populating the exchange with received message");
        Exchange exchange = consumer.getEndpoint().createExchange(ExchangePattern.InOut);
        // TODO: Evaluate the CHARSET handling - may not be correct
        exchange.setProperty(Exchange.CHARSET_NAME, consumer.getEndpoint().determineCharset(hl7MessageBytes, null));
        try {
            consumer.createUoW(exchange);
            Message message = exchange.getIn();

            if (localAddress != null) {
                message.setHeader(MllpConstants.MLLP_LOCAL_ADDRESS, localAddress);
            }

            if (remoteAddress != null) {
                message.setHeader(MllpConstants.MLLP_REMOTE_ADDRESS, remoteAddress);
            }
            message.setHeader(MllpConstants.MLLP_AUTO_ACKNOWLEDGE, consumer.getConfiguration().isAutoAck());

            if (consumer.getConfiguration().isValidatePayload()) {
                String exceptionMessage = Hl7Util.generateInvalidPayloadExceptionMessage(hl7MessageBytes);
                if (exceptionMessage != null) {
                    exchange.setException(new MllpInvalidMessageException(exceptionMessage, hl7MessageBytes));
                }
            }
            populateHl7DataHeaders(exchange, message, hl7MessageBytes);

            if (consumer.getConfiguration().isStringPayload()) {
                if (hl7MessageBytes != null && hl7MessageBytes.length > 0) {
                    message.setBody(consumer.getEndpoint().createNewString(hl7MessageBytes, message.getHeader(MllpConstants.MLLP_CHARSET, String.class)), String.class);
                } else {
                    message.setBody("", String.class);
                }
            } else {
                message.setBody(hl7MessageBytes, byte[].class);
            }

            log.debug("Calling processor");
            try {
                consumer.getProcessor().process(exchange);
                sendAcknowledgement(hl7MessageBytes, exchange);
            } catch (RuntimeException runtimeEx) {
                throw runtimeEx;
            } catch (Exception ex) {
                log.error("Unexpected exception processing exchange", ex);
            }
        } catch (Exception uowEx) {
            // TODO:  Handle this correctly
            exchange.setException(uowEx);
            log.warn("Exception encountered creating Unit of Work - sending exception to route", uowEx);
            try {
                consumer.getProcessor().process(exchange);
            } catch (Exception e) {
                log.error("Exception encountered processing exchange with exception encountered createing Unit of Work", e);
            }
            return;
        } finally {
            if (exchange != null) {
                consumer.doneUoW(exchange);
            }
        }
    }

    @Override
    public void run() {
        running = true;
        String originalThreadName = Thread.currentThread().getName();
        Thread.currentThread().setName(createThreadName(clientSocket));
        MDC.put(MDCUnitOfWork.MDC_CAMEL_CONTEXT_ID, consumer.getEndpoint().getCamelContext().getName());

        Route route = consumer.getRoute();
        if (route != null) {
            String routeId = route.getId();
            if (routeId != null) {
                MDC.put(MDCUnitOfWork.MDC_ROUTE_ID, route.getId());
            }
        }

        log.debug("Starting {} for {}", this.getClass().getSimpleName(), combinedAddress);
        try {
            while (running && null != clientSocket && clientSocket.isConnected() && !clientSocket.isClosed()) {
                byte[] hl7MessageBytes = null;
                log.debug("Checking for data ....");
                try {
                    mllpBuffer.readFrom(clientSocket);
                    if (mllpBuffer.hasCompleteEnvelope()) {
                        hl7MessageBytes = mllpBuffer.toMllpPayload();
                        log.debug("Received {} byte message {}", hl7MessageBytes.length, Hl7Util.convertToPrintFriendlyString(hl7MessageBytes));
                        if (mllpBuffer.hasLeadingOutOfBandData()) {
                            // TODO:  Move the convertion utilities to the MllpSocketBuffer to avoid a byte[] copy
                            log.warn("Ignoring leading out-of-band data: {}", Hl7Util.convertToPrintFriendlyString(mllpBuffer.getLeadingOutOfBandData()));
                        }
                        if (mllpBuffer.hasTrailingOutOfBandData()) {
                            log.warn("Ignoring trailing out-of-band data: {}", Hl7Util.convertToPrintFriendlyString(mllpBuffer.getTrailingOutOfBandData()));
                        }
                        mllpBuffer.reset();

                        processMessage(hl7MessageBytes);
                    } else if (!mllpBuffer.hasStartOfBlock()) {
                        byte[] payload = mllpBuffer.toByteArray();
                        log.warn("Ignoring {} byte un-enveloped payload {}", payload.length, Hl7Util.convertToPrintFriendlyString(payload));
                        mllpBuffer.reset();
                    } else if (!mllpBuffer.isEmpty()) {
                        byte[] payload = mllpBuffer.toByteArray();
                        log.warn("Partial {} byte payload received {}", payload.length, Hl7Util.convertToPrintFriendlyString(payload));
                    }
                } catch (SocketTimeoutException timeoutEx) {
                    if (mllpBuffer.isEmpty()) {
                        if (consumer.getConfiguration().hasIdleTimeout()) {
                            long currentTicks = System.currentTimeMillis();
                            long lastReceivedMessageTicks = consumer.getConsumerRunnables().get(this);
                            long idleTime = currentTicks - lastReceivedMessageTicks;
                            if (idleTime >= consumer.getConfiguration().getIdleTimeout()) {
                                consumer.getEndpoint().doConnectionClose(clientSocket, true, log);
                            }
                        }
                        log.info("No data received - ignoring timeout");
                    } else {
                        mllpBuffer.resetSocket(clientSocket);
                        if (consumer.getEndpoint().isBridgeErrorHandler()) {
                            Exchange exchange = consumer.getEndpoint().createExchange(ExchangePattern.InOut);
                            exchange.setException(new MllpInvalidMessageException("Timeout receiving complete payload", mllpBuffer.toByteArray()));
                            log.warn("Exception encountered reading payload - sending exception to route", exchange.getException());
                            try {
                                consumer.getProcessor().process(exchange);
                            } catch (Exception e) {
                                log.error("Exception encountered processing exchange with exception encounter reading payload", e);
                            }
                        } else {
                            log.error("Timeout receiving complete payload", new MllpInvalidMessageException("Timeout receiving complete payload", mllpBuffer.toByteArray(), timeoutEx));
                        }
                    }
                } catch (MllpSocketException mllpSocketEx) {
                    if (!mllpBuffer.isEmpty()) {
                        Exchange exchange = consumer.getEndpoint().createExchange(ExchangePattern.InOut);
                        exchange.setException(new MllpReceiveException("Exception encountered reading payload", mllpBuffer.toByteArrayAndReset(), mllpSocketEx));
                        try {
                            consumer.getProcessor().process(exchange);
                        } catch (Exception ignoredEx) {
                            log.error("Ingnoring exception encountered processing exchange with exception encounter reading payload", ignoredEx);
                        }
                    } else {
                        log.warn("Ignoring exception encountered checking for data", mllpSocketEx);
                    }
                }
            }
        } catch (Exception unexpectedEx) {
            log.error("Unexpected exception encountered receiving messages", unexpectedEx);
        } finally {
            consumer.getConsumerRunnables().remove(this);
            log.debug("{} for {} completed", this.getClass().getSimpleName(), combinedAddress);

            Thread.currentThread().setName(originalThreadName);
            MDC.remove(MDCUnitOfWork.MDC_ROUTE_ID);
            MDC.remove(MDCUnitOfWork.MDC_CAMEL_CONTEXT_ID);
        }
    }

    public void closeSocket() {
        mllpBuffer.closeSocket(clientSocket);
    }

    public void closeSocket(String logMessage) {
        mllpBuffer.closeSocket(clientSocket, logMessage);
    }

    public void resetSocket() {
        mllpBuffer.resetSocket(clientSocket);
    }

    public void resetSocket(String logMessage) {
        mllpBuffer.resetSocket(clientSocket, logMessage);
    }

    public void stop() {
        running = false;
    }

    public String getLocalAddress() {
        return localAddress;
    }

    public String getRemoteAddress() {
        return remoteAddress;
    }

    public String getCombinedAddress() {
        return combinedAddress;
    }
}
