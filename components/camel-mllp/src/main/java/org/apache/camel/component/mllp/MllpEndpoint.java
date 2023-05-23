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
package org.apache.camel.component.mllp;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Date;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.slf4j.Logger;

/**
 * Communicate with external systems using the MLLP protocol.
 *
 * <p/>
 * NOTE: MLLP payloads are not logged unless the logging level is set to DEBUG or TRACE to avoid introducing PHI into
 * the log files. Logging of PHI can be globally disabled by setting the org.apache.camel.mllp.logPHI system property to
 * false.
 * <p/>
 */
@ManagedResource(description = "MLLP Endpoint")
@UriEndpoint(scheme = "mllp", firstVersion = "2.17.0", title = "MLLP", syntax = "mllp:hostname:port",
             category = { Category.HEALTH }, generateConfigurer = true,
             headersClass = MllpConstants.class)
public class MllpEndpoint extends DefaultEndpoint {

    @UriPath
    @Metadata(required = true)
    String hostname;

    @UriPath
    @Metadata(required = true)
    int port = -1;

    @UriParam(label = "advanced")
    MllpConfiguration configuration;

    Long lastConnectionActivityTicks;
    Long lastConnectionEstablishedTicks;
    Long lastConnectionTerminatedTicks;

    public MllpEndpoint(String uri, MllpComponent component, MllpConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration.copy();

        super.setBridgeErrorHandler(configuration.isBridgeErrorHandler());
        super.setExchangePattern(configuration.getExchangePattern());
    }

    @Override
    public MllpComponent getComponent() {
        return (MllpComponent) super.getComponent();
    }

    @Override
    public Exchange createExchange(ExchangePattern exchangePattern) {
        Exchange mllpExchange = super.createExchange(exchangePattern);
        setExchangeProperties(mllpExchange);
        return mllpExchange;
    }

    @Override
    public void setExchangePattern(ExchangePattern exchangePattern) {
        configuration.setExchangePattern(exchangePattern);
        super.setExchangePattern(configuration.getExchangePattern());
    }

    @Override
    public void setBridgeErrorHandler(boolean bridgeErrorHandler) {
        configuration.setBridgeErrorHandler(bridgeErrorHandler);
        super.setBridgeErrorHandler(configuration.isBridgeErrorHandler());
    }

    void setExchangeProperties(Exchange mllpExchange) {
        if (configuration.hasCharsetName()) {
            mllpExchange.setProperty(ExchangePropertyKey.CHARSET_NAME, configuration.getCharsetName());
        }
    }

    @Override
    public Producer createProducer() throws Exception {
        return new MllpTcpClientProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        Consumer consumer = new MllpTcpServerConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    @ManagedAttribute(description = "Last activity time")
    public Date getLastConnectionActivityTime() {
        if (lastConnectionActivityTicks != null) {
            return new Date(lastConnectionActivityTicks);
        }

        return null;
    }

    @ManagedAttribute(description = "Last connection established time")
    public Date getLastConnectionEstablishedTime() {
        if (lastConnectionEstablishedTicks != null) {
            return new Date(lastConnectionEstablishedTicks);
        }

        return null;
    }

    @ManagedAttribute(description = "Last connection terminated time")
    public Date getLastConnectionTerminatedTime() {
        return lastConnectionTerminatedTicks != null ? new Date(lastConnectionTerminatedTicks) : null;
    }

    public boolean hasLastConnectionActivityTicks() {
        return lastConnectionActivityTicks != null && lastConnectionActivityTicks > 0;
    }

    public Long getLastConnectionActivityTicks() {
        return lastConnectionActivityTicks;
    }

    public void updateLastConnectionActivityTicks() {
        updateLastConnectionActivityTicks(System.currentTimeMillis());
    }

    public void updateLastConnectionActivityTicks(long epochTicks) {
        lastConnectionActivityTicks = epochTicks;
    }

    public void updateLastConnectionEstablishedTicks() {
        lastConnectionEstablishedTicks = System.currentTimeMillis();
    }

    public void updateLastConnectionTerminatedTicks() {
        lastConnectionTerminatedTicks = System.currentTimeMillis();
    }

    public String getHostname() {
        return hostname;
    }

    /**
     * Hostname or IP for connection for the TCP connection.
     *
     * The default value is null, which means any local IP address
     *
     * @param hostname Hostname or IP
     */
    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public int getPort() {
        return port;
    }

    /**
     * Port number for the TCP connection
     *
     * @param port TCP port
     */
    public void setPort(int port) {
        this.port = port;
    }

    public boolean hasConfiguration() {
        return configuration != null;
    }

    public MllpConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(MllpConfiguration configuration) {
        if (hasConfiguration()) {
            this.configuration.copy(configuration);
        } else {
            this.configuration = configuration.copy();
        }
    }

    // Pass-through configuration methods
    public void setBacklog(Integer backlog) {
        configuration.setBacklog(backlog);
    }

    public void setBindTimeout(int bindTimeout) {
        configuration.setBindTimeout(bindTimeout);
    }

    public void setBindRetryInterval(int bindRetryInterval) {
        configuration.setBindRetryInterval(bindRetryInterval);
    }

    public void setLenientBind(boolean lenientBind) {
        configuration.setLenientBind(lenientBind);
    }

    public void setAcceptTimeout(int acceptTimeout) {
        configuration.setAcceptTimeout(acceptTimeout);
    }

    public void setConnectTimeout(int connectTimeout) {
        configuration.setConnectTimeout(connectTimeout);
    }

    public void setReceiveTimeout(int receiveTimeout) {
        configuration.setReceiveTimeout(receiveTimeout);
    }

    public void setIdleTimeout(Integer idleTimeout) {
        configuration.setIdleTimeout(idleTimeout);
    }

    public void setReadTimeout(int readTimeout) {
        configuration.setReadTimeout(readTimeout);
    }

    public void setKeepAlive(Boolean keepAlive) {
        configuration.setKeepAlive(keepAlive);
    }

    public void setTcpNoDelay(Boolean tcpNoDelay) {
        configuration.setTcpNoDelay(tcpNoDelay);
    }

    public void setReuseAddress(Boolean reuseAddress) {
        configuration.setReuseAddress(reuseAddress);
    }

    public void setReceiveBufferSize(Integer receiveBufferSize) {
        configuration.setReceiveBufferSize(receiveBufferSize);
    }

    public void setSendBufferSize(Integer sendBufferSize) {
        configuration.setSendBufferSize(sendBufferSize);
    }

    public void setAutoAck(Boolean autoAck) {
        configuration.setAutoAck(autoAck);
    }

    public void setHl7Headers(Boolean hl7Headers) {
        configuration.setHl7Headers(hl7Headers);
    }

    public void setRequireEndOfData(Boolean requireEndOfData) {
        configuration.setRequireEndOfData(requireEndOfData);
    }

    public void setStringPayload(Boolean stringPayload) {
        configuration.setStringPayload(stringPayload);
    }

    public void setValidatePayload(Boolean validatePayload) {
        configuration.setValidatePayload(validatePayload);
    }

    public String getCharsetName() {
        return configuration.getCharsetName();
    }

    public void setCharsetName(String charsetName) {
        configuration.setCharsetName(charsetName);
    }

    public void setMaxConcurrentConsumers(int maxConcurrentConsumers) {
        configuration.setMaxConcurrentConsumers(maxConcurrentConsumers);
    }

    public void setIdleTimeoutStrategy(MllpIdleTimeoutStrategy strategy) {
        configuration.setIdleTimeoutStrategy(strategy);
    }

    // Utility methods for producers and consumers

    public boolean checkBeforeSendProperties(Exchange exchange, Socket socket, Logger log) {
        final String logMessageFormat = "Exchange property {} = {} - {} connection";
        boolean answer = true;

        final boolean resetBeforeSend
                = exchange.getProperty(MllpConstants.MLLP_RESET_CONNECTION_BEFORE_SEND, false, boolean.class);
        if (resetBeforeSend) {
            log.warn(logMessageFormat, MllpConstants.MLLP_RESET_CONNECTION_BEFORE_SEND,
                    exchange.getProperty(MllpConstants.MLLP_RESET_CONNECTION_BEFORE_SEND), "resetting");
            doConnectionClose(socket, true, null);
            answer = false;
        } else {
            final boolean closeBeforeSend
                    = exchange.getProperty(MllpConstants.MLLP_CLOSE_CONNECTION_BEFORE_SEND, false, boolean.class);
            if (closeBeforeSend) {
                log.warn(logMessageFormat, MllpConstants.MLLP_CLOSE_CONNECTION_BEFORE_SEND,
                        exchange.getProperty(MllpConstants.MLLP_CLOSE_CONNECTION_BEFORE_SEND), "closing");
                doConnectionClose(socket, false, null);
                answer = false;
            }
        }

        return answer;
    }

    public boolean checkAfterSendProperties(Exchange exchange, Socket socket, Logger log) {
        final String logMessageFormat = "Exchange property {} = {} - {} connection";
        boolean answer = true;

        final boolean resetAfterSend
                = exchange.getProperty(MllpConstants.MLLP_RESET_CONNECTION_AFTER_SEND, false, boolean.class);
        if (resetAfterSend) {
            log.warn(logMessageFormat, MllpConstants.MLLP_RESET_CONNECTION_AFTER_SEND,
                    exchange.getProperty(MllpConstants.MLLP_RESET_CONNECTION_AFTER_SEND), "resetting");
            doConnectionClose(socket, true, log);
            answer = false;
        } else {
            final boolean closeAfterSend
                    = exchange.getProperty(MllpConstants.MLLP_CLOSE_CONNECTION_AFTER_SEND, false, boolean.class);
            if (closeAfterSend) {
                log.warn(logMessageFormat, MllpConstants.MLLP_CLOSE_CONNECTION_AFTER_SEND,
                        exchange.getProperty(MllpConstants.MLLP_CLOSE_CONNECTION_AFTER_SEND), "closing");
                doConnectionClose(socket, false, log);
                answer = false;
            }
        }

        return answer;
    }

    public void doConnectionClose(Socket socket, boolean reset, Logger log) {
        String ignoringCallLogFormat = "Ignoring {} Connection request because - {}: localAddress={} remoteAddress={}";

        if (socket == null) {
            if (log != null) {
                log.debug(ignoringCallLogFormat, reset ? "Reset" : "Close", "Socket is null", "null", "null");
            }
        } else {
            SocketAddress localSocketAddress = socket.getLocalSocketAddress();
            SocketAddress remoteSocketAddress = socket.getRemoteSocketAddress();
            if (!socket.isConnected()) {
                if (log != null) {
                    log.debug(ignoringCallLogFormat, reset ? "Reset" : "Close", "Socket is not connected", localSocketAddress,
                            remoteSocketAddress);
                }
            } else if (socket.isClosed()) {
                if (log != null) {
                    log.debug(ignoringCallLogFormat, reset ? "Reset" : "Close", "Socket is already closed", localSocketAddress,
                            remoteSocketAddress);
                }
            } else {
                this.updateLastConnectionTerminatedTicks();
                final String ignoringExceptionStringFormat
                        = "Ignoring %s encountered calling %s on Socket: localAddress=%s remoteAddress=%s";
                if (!socket.isInputShutdown()) {
                    if (log != null) {
                        log.trace("Shutting down input on Socket: localAddress={} remoteAddress={}", localSocketAddress,
                                remoteSocketAddress);
                    }
                    try {
                        socket.shutdownInput();
                    } catch (Exception ioEx) {
                        if (log != null && log.isDebugEnabled()) {
                            String logMessage = String.format(ignoringExceptionStringFormat, ioEx.getClass().getSimpleName(),
                                    "shutdownInput()", localSocketAddress, remoteSocketAddress);
                            log.debug(logMessage, ioEx);
                        }
                    }
                }

                if (!socket.isOutputShutdown()) {
                    if (log != null) {
                        log.trace("Shutting down output on Socket: localAddress={} remoteAddress={}", localSocketAddress,
                                remoteSocketAddress);
                    }
                    try {
                        socket.shutdownOutput();
                    } catch (IOException ioEx) {
                        if (log != null && log.isDebugEnabled()) {
                            String logMessage = String.format(ignoringExceptionStringFormat, ioEx.getClass().getSimpleName(),
                                    "shutdownOutput()", localSocketAddress, remoteSocketAddress);
                            log.debug(logMessage, ioEx);
                        }
                    }
                }

                if (reset) {
                    final boolean on = true;
                    final int linger = 0;
                    if (log != null) {
                        log.trace("Setting SO_LINGER to {} on Socket: localAddress={} remoteAddress={}", linger,
                                localSocketAddress, remoteSocketAddress);
                    }
                    try {
                        socket.setSoLinger(on, linger);
                    } catch (IOException ioEx) {
                        if (log != null && log.isDebugEnabled()) {
                            String methodString = String.format("setSoLinger(%b, %d)", on, linger);
                            String logMessage = String.format(ignoringExceptionStringFormat, ioEx.getClass().getSimpleName(),
                                    methodString, localSocketAddress, remoteSocketAddress);
                            log.debug(logMessage, ioEx);
                        }
                    }
                }

                try {
                    if (log != null) {
                        log.trace("Resetting Socket: localAddress={} remoteAddress={}", localSocketAddress,
                                remoteSocketAddress);
                    }
                    socket.close();
                } catch (IOException ioEx) {
                    if (log != null && log.isDebugEnabled()) {
                        String warningMessage = String.format(ignoringExceptionStringFormat, ioEx.getClass().getSimpleName(),
                                "close()", localSocketAddress, remoteSocketAddress);
                        log.debug(warningMessage, ioEx);
                    }
                }
            }
        }
    }
}
