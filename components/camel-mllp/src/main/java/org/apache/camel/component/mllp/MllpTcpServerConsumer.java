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

package org.apache.camel.component.mllp;

import java.io.IOException;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.component.mllp.internal.Hl7Util;
import org.apache.camel.component.mllp.internal.MllpSocketBuffer;
import org.apache.camel.component.mllp.internal.TcpServerAcceptThread;
import org.apache.camel.component.mllp.internal.TcpServerBindThread;
import org.apache.camel.component.mllp.internal.TcpServerConsumerValidationRunnable;
import org.apache.camel.component.mllp.internal.TcpSocketConsumerRunnable;
import org.apache.camel.converter.IOConverter;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.camel.processor.mllp.Hl7AcknowledgementGenerationException;
import org.apache.camel.util.IOHelper;

/**
 * The MLLP consumer.
 */
@ManagedResource(description = "MLLP Producer")
public class MllpTcpServerConsumer extends DefaultConsumer {
    final ExecutorService validationExecutor;
    final ExecutorService consumerExecutor;

    TcpServerBindThread bindThread;
    TcpServerAcceptThread acceptThread;

    Map<TcpSocketConsumerRunnable, Long> consumerRunnables = new ConcurrentHashMap<>();


    public MllpTcpServerConsumer(MllpEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        log.trace("MllpTcpServerConsumer(endpoint, processor)");

        validationExecutor = Executors.newCachedThreadPool();
        consumerExecutor = new ThreadPoolExecutor(1, getConfiguration().getMaxConcurrentConsumers(), getConfiguration().getAcceptTimeout(), TimeUnit.MILLISECONDS, new SynchronousQueue<>());
    }

    @ManagedAttribute(description = "Last activity time")
    public Map<String, Date> getLastActivityTimes() {
        Map<String, Date> answer = new HashMap<>();

        for (Map.Entry<TcpSocketConsumerRunnable, Long> entry : consumerRunnables.entrySet()) {
            TcpSocketConsumerRunnable consumerRunnable = entry.getKey();
            if (consumerRunnable != null) {
                answer.put(consumerRunnable.getCombinedAddress(), new Date(entry.getValue()));
            }
        }
        return answer;
    }

    @ManagedOperation(description = "Close Connections")
    public void closeConnections() {

        for (TcpSocketConsumerRunnable consumerRunnable : consumerRunnables.keySet()) {
            if (consumerRunnable != null) {
                log.info("Close Connection called via JMX for address {}", consumerRunnable.getCombinedAddress());
                consumerRunnable.closeSocket();
            }
        }
    }

    @ManagedOperation(description = "Reset Connections")
    public void resetConnections() {

        for (TcpSocketConsumerRunnable consumerRunnable : consumerRunnables.keySet()) {
            if (consumerRunnable != null) {
                log.info("Reset Connection called via JMX for address {}", consumerRunnable.getCombinedAddress());
                consumerRunnable.resetSocket();
            }
        }
    }

    @Override
    public MllpEndpoint getEndpoint() {
        return (MllpEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStop() throws Exception {
        log.debug("doStop()");

        // Close any client sockets that are currently open
        for (TcpSocketConsumerRunnable consumerClientSocketThread : consumerRunnables.keySet()) {
            consumerClientSocketThread.stop();
        }

        if (acceptThread != null) {
            acceptThread.interrupt();
            acceptThread = null;
        }

        if (bindThread != null) {
            bindThread.interrupt();
            bindThread = null;
        }

        super.doStop();
    }

    @Override
    protected void doStart() throws Exception {
        if (bindThread == null || !bindThread.isAlive()) {
            bindThread = new TcpServerBindThread(this);

            if (getConfiguration().isLenientBind()) {
                log.debug("doStart() - starting bind thread");
                bindThread.start();
            } else {
                log.debug("doStart() - attempting to bind to port {}", getEndpoint().getPort());
                bindThread.run();

                if (this.acceptThread == null) {
                    throw new BindException("Failed to bind to port " + getEndpoint().getPort());
                }
            }
        }

        super.doStart();
    }


    @Override
    protected void doShutdown() throws Exception {
        super.doShutdown();
        consumerExecutor.shutdownNow();
        if (acceptThread != null) {
            acceptThread.interrupt();
        }
        validationExecutor.shutdownNow();
    }


    public void handleMessageTimeout(String message, byte[] payload, Throwable cause) {
        super.handleException(new MllpInvalidMessageException(message, payload, cause));
    }

    public void handleMessageException(String message, byte[] payload, Throwable cause) {
        super.handleException(new MllpReceiveException(message, payload, cause));
    }

    public MllpConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }


    public Map<TcpSocketConsumerRunnable, Long> getConsumerRunnables() {
        return consumerRunnables;
    }


    public void validateConsumer(Socket clientSocket) {
        MllpSocketBuffer mllpBuffer = new MllpSocketBuffer(getEndpoint());
        TcpServerConsumerValidationRunnable client = new TcpServerConsumerValidationRunnable(this, clientSocket, mllpBuffer);

        try {
            log.debug("Validating consumer for Socket {}", clientSocket);
            validationExecutor.submit(client);
        } catch (RejectedExecutionException rejectedExecutionEx) {
            log.warn("Cannot validate consumer - max validations already active");
            mllpBuffer.resetSocket(clientSocket);
        }
    }

    public void startAcceptThread(ServerSocket serverSocket) {
        acceptThread = new TcpServerAcceptThread(this, serverSocket);
        acceptThread.start();
    }

    public void startConsumer(Socket clientSocket, MllpSocketBuffer mllpBuffer) {
        TcpSocketConsumerRunnable client = new TcpSocketConsumerRunnable(this, clientSocket, mllpBuffer);

        consumerRunnables.put(client, System.currentTimeMillis());
        try {
            log.info("Starting consumer for Socket {}", clientSocket);
            consumerExecutor.submit(client);
        } catch (RejectedExecutionException rejectedExecutionEx) {
            log.warn("Cannot start consumer - max consumers already active");
            mllpBuffer.resetSocket(clientSocket);
        }
    }

    public void processMessage(byte[] hl7MessageBytes, TcpSocketConsumerRunnable consumerRunnable) {
        getEndpoint().updateLastConnectionActivityTicks();

        // Send the message on to Camel for processing and wait for the response
        log.debug("Populating the exchange with received message");
        Exchange exchange = getEndpoint().createExchange(ExchangePattern.InOut);
        if (getConfiguration().hasCharsetName()) {
            exchange.setProperty(Exchange.CHARSET_NAME, getConfiguration().getCharsetName());
        }
        try {
            createUoW(exchange);
            Message message = exchange.getIn();

            if (consumerRunnable.hasLocalAddress()) {
                message.setHeader(MllpConstants.MLLP_LOCAL_ADDRESS, consumerRunnable.getLocalAddress());
            }

            if (consumerRunnable.hasRemoteAddress()) {
                message.setHeader(MllpConstants.MLLP_REMOTE_ADDRESS, consumerRunnable.getRemoteAddress());
            }

            if (message.hasHeaders() && message.getHeader(MllpConstants.MLLP_AUTO_ACKNOWLEDGE) == null) {
                message.setHeader(MllpConstants.MLLP_AUTO_ACKNOWLEDGE, getConfiguration().isAutoAck());
            }

            if (getConfiguration().isValidatePayload()) {
                String exceptionMessage = Hl7Util.generateInvalidPayloadExceptionMessage(hl7MessageBytes);
                if (exceptionMessage != null) {
                    exchange.setException(new MllpInvalidMessageException(exceptionMessage, hl7MessageBytes));
                }
            }
            populateHl7DataHeaders(exchange, message, hl7MessageBytes);

            if (getConfiguration().isStringPayload()) {
                if (hl7MessageBytes != null && hl7MessageBytes.length > 0) {
                    message.setBody(new String(hl7MessageBytes, getConfiguration().getCharset(exchange, hl7MessageBytes)));
                } else {
                    message.setBody("", String.class);
                }
            } else {
                message.setBody(hl7MessageBytes, byte[].class);
            }

            log.debug("Calling processor");
            try {
                getProcessor().process(exchange);
                sendAcknowledgement(hl7MessageBytes, exchange, consumerRunnable);
            } catch (Exception unexpectedEx) {
                getExceptionHandler().handleException("Unexpected exception processing exchange", exchange, unexpectedEx);
            }
        } catch (Exception uowEx) {
            getExceptionHandler().handleException("Unexpected exception creating Unit of Work", exchange, uowEx);
        } finally {
            if (exchange != null) {
                doneUoW(exchange);
            }
        }
    }


    void populateHl7DataHeaders(Exchange exchange, Message message, byte[] hl7MessageBytes) {
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
            } else if (getConfiguration().isHl7Headers()) {
                log.debug("Populating the HL7 message headers");
                Charset charset = getConfiguration().getCharset(exchange);

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

                        String headerValue = (i == 17 && getConfiguration().hasCharsetName())
                            ? getConfiguration().getCharsetName()
                            : new String(hl7MessageBytes, startingFieldSeparatorIndex + 1, endingFieldSeparatorIndex - startingFieldSeparatorIndex - 1, charset);

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


    void sendAcknowledgement(byte[] originalHl7MessageBytes, Exchange exchange, TcpSocketConsumerRunnable consumerRunnable) {
        log.trace("entering sendAcknowledgement(byte[], Exchange)");

        getEndpoint().checkBeforeSendProperties(exchange, consumerRunnable.getSocket(), log);

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

                    Hl7Util.generateAcknowledgementPayload(consumerRunnable.getMllpBuffer(), originalHl7MessageBytes, acknowledgementMessageType);

                } catch (Hl7AcknowledgementGenerationException ackGenerationException) {
                    exchange.setProperty(MllpConstants.MLLP_ACKNOWLEDGEMENT_EXCEPTION, ackGenerationException);
                    exchange.setException(ackGenerationException);
                }
            }
        } else {
            consumerRunnable.getMllpBuffer().setEnvelopedMessage(acknowledgementMessageBytes);

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

        Charset charset = getConfiguration().getCharset(exchange);

        if (consumerRunnable.getMllpBuffer().hasCompleteEnvelope()) {
            // The mllpBuffer will be used if bufferWrites is set or if auto acknowledgement is used
            message.setHeader(MllpConstants.MLLP_ACKNOWLEDGEMENT, consumerRunnable.getMllpBuffer().toMllpPayload());
            message.setHeader(MllpConstants.MLLP_ACKNOWLEDGEMENT_STRING, consumerRunnable.getMllpBuffer().toHl7String(charset));

            // Send the acknowledgement
            if (log.isDebugEnabled()) {
                log.debug("Sending Acknowledgement: {}", consumerRunnable.getMllpBuffer().toPrintFriendlyHl7String());
            }

            try {
                consumerRunnable.getMllpBuffer().writeTo(consumerRunnable.getSocket());
            } catch (MllpSocketException acknowledgementDeliveryEx) {
                Exception exchangeEx = new MllpAcknowledgementDeliveryException("Failure delivering acknowledgment", originalHl7MessageBytes, acknowledgementMessageBytes, acknowledgementDeliveryEx);
                exchange.setProperty(MllpConstants.MLLP_ACKNOWLEDGEMENT_EXCEPTION, acknowledgementDeliveryEx);
                exchange.setException(exchangeEx);
            } finally {
                consumerRunnable.getMllpBuffer().reset();
            }
        } else if (acknowledgementMessageBytes != null && acknowledgementMessageBytes.length > 0) {
            message.setHeader(MllpConstants.MLLP_ACKNOWLEDGEMENT, acknowledgementMessageBytes);
            String acknowledgementMessageString = new String(acknowledgementMessageBytes, charset);
            message.setHeader(MllpConstants.MLLP_ACKNOWLEDGEMENT_STRING, acknowledgementMessageString);

            // Send the acknowledgement
            if (log.isDebugEnabled()) {
                log.debug("Sending Acknowledgement: {}", Hl7Util.convertToPrintFriendlyString(acknowledgementMessageBytes));
            }

            try {
                consumerRunnable.getMllpBuffer().setEnvelopedMessage(acknowledgementMessageBytes);
                consumerRunnable.getMllpBuffer().writeTo(consumerRunnable.getSocket());
            } catch (MllpSocketException acknowledgementDeliveryEx) {
                Exception exchangeEx = new MllpAcknowledgementDeliveryException("Failure delivering acknowledgment", originalHl7MessageBytes, acknowledgementMessageBytes, acknowledgementDeliveryEx);
                exchange.setProperty(MllpConstants.MLLP_ACKNOWLEDGEMENT_EXCEPTION, acknowledgementDeliveryEx);
                exchange.setException(exchangeEx);
            }
        }

        getEndpoint().checkAfterSendProperties(exchange, consumerRunnable.getSocket(), log);
    }


}
