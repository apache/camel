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

import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides functionality required by Healthcare providers to communicate with other systems using the MLLP protocol.
 * <p/>
 * MLLP payloads are not logged unless the logging level is set to DEBUG or TRACE to avoid introducing PHI
 * into the log files. Logging of PHI can be globally disabled by setting the org.apache.camel.mllp.logPHI system
 * property to false.
 */
@ManagedResource(description = "Mllp Endpoint")
@UriEndpoint(firstVersion = "2.17.0", scheme = "mllp", title = "MLLP", syntax = "mllp:hostname:port", consumerClass = MllpTcpServerConsumer.class, label = "hl7")
public class MllpEndpoint extends DefaultEndpoint {
    public static final char START_OF_BLOCK = 0x0b;      // VT (vertical tab)        - decimal 11, octal 013
    public static final char END_OF_BLOCK = 0x1c;        // FS (file separator)      - decimal 28, octal 034
    public static final char END_OF_DATA = 0x0d;         // CR (carriage return)     - decimal 13, octal 015
    public static final int END_OF_STREAM = -1;          //
    public static final char SEGMENT_DELIMITER = 0x0d;   // CR (carriage return)     - decimal 13, octal 015
    public static final char MESSAGE_TERMINATOR = 0x0a;  // LF (line feed, new line) - decimal 10, octal 012

    private static final Logger LOG = LoggerFactory.getLogger(MllpEndpoint.class);

    @UriPath @Metadata(required = "true")
    String hostname;

    @UriPath @Metadata(required = "true")
    int port = -1;

    @UriParam(label = "advanced", defaultValue = "5")
    int backlog = 5;

    @UriParam(label = "timeout", defaultValue = "30000")
    int bindTimeout = 30000;

    @UriParam(label = "timeout", defaultValue = "5000")
    int bindRetryInterval = 5000;

    @UriParam(label = "timeout", defaultValue = "60000")
    int acceptTimeout = 60000;

    @UriParam(label = "timeout", defaultValue = "30000")
    int connectTimeout = 30000;

    @UriParam(label = "timeout", defaultValue = "10000")
    int receiveTimeout = 10000;

    @UriParam(label = "timeout", defaultValue = "-1")
    int maxReceiveTimeouts = -1;

    @UriParam(label = "timeout", defaultValue = "500")
    int readTimeout = 500;

    @UriParam(defaultValue = "true")
    boolean keepAlive = true;

    @UriParam(defaultValue = "true")
    boolean tcpNoDelay = true;

    @UriParam
    boolean reuseAddress;

    @UriParam(label = "advanced")
    Integer receiveBufferSize;

    @UriParam(label = "advanced")
    Integer sendBufferSize;

    @UriParam(defaultValue = "true")
    boolean autoAck = true;

    @UriParam(defaultValue = "true")
    boolean hl7Headers = true;

    @UriParam(defaultValue = "true")
    boolean bufferWrites = true;

    @UriParam(defaultValue = "false")
    boolean validatePayload;

    @UriParam(label = "codec")
    String charsetName;

    public MllpEndpoint(String uri, MllpComponent component) {
        super(uri, component);
    }

    @Override
    public ExchangePattern getExchangePattern() {
        return ExchangePattern.InOut;
    }

    @Override
    public Exchange createExchange(ExchangePattern exchangePattern) {
        Exchange mllpExchange = super.createExchange(exchangePattern);
        setExchangeProperties(mllpExchange);
        return mllpExchange;
    }

    @Override
    public Exchange createExchange(Exchange exchange) {
        Exchange mllpExchange = super.createExchange(exchange);
        setExchangeProperties(mllpExchange);
        return mllpExchange;
    }

    private void setExchangeProperties(Exchange mllpExchange) {
        if (charsetName != null) {
            mllpExchange.setProperty(Exchange.CHARSET_NAME, charsetName);
        }
    }

    public Producer createProducer() throws Exception {
        LOG.trace("({}).createProducer()", this.getEndpointKey());
        return new MllpTcpClientProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        LOG.trace("({}).createConsumer(processor)", this.getEndpointKey());
        Consumer consumer = new MllpTcpServerConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    public boolean isSynchronous() {
        return true;
    }

    public boolean isSingleton() {
        return true;
    }

    public String getCharsetName() {
        return charsetName;
    }

    /**
     * Set the CamelCharsetName property on the exchange
     *
     * @param charsetName the charset
     */
    public void setCharsetName(String charsetName) {
        this.charsetName = charsetName;
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

    public int getBacklog() {
        return backlog;
    }

    /**
     * The maximum queue length for incoming connection indications (a request to connect) is set to the backlog parameter. If a connection indication arrives when the queue is full, the connection
     * is refused.
     */
    public void setBacklog(int backlog) {
        this.backlog = backlog;
    }

    public int getBindTimeout() {
        return bindTimeout;
    }

    /**
     * TCP Server Only - The number of milliseconds to retry binding to a server port
     */
    public void setBindTimeout(int bindTimeout) {
        this.bindTimeout = bindTimeout;
    }

    public int getBindRetryInterval() {
        return bindRetryInterval;
    }

    /**
     * TCP Server Only - The number of milliseconds to wait between bind attempts
     */
    public void setBindRetryInterval(int bindRetryInterval) {
        this.bindRetryInterval = bindRetryInterval;
    }

    public int getAcceptTimeout() {
        return acceptTimeout;
    }

    /**
     * Timeout (in milliseconds) while waiting for a TCP connection
     * <p/>
     * TCP Server Only
     *
     * @param acceptTimeout timeout in milliseconds
     */
    public void setAcceptTimeout(int acceptTimeout) {
        this.acceptTimeout = acceptTimeout;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    /**
     * Timeout (in milliseconds) for establishing for a TCP connection
     * <p/>
     * TCP Client only
     *
     * @param connectTimeout timeout in milliseconds
     */
    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public int getReceiveTimeout() {
        return receiveTimeout;
    }

    /**
     * The SO_TIMEOUT value (in milliseconds) used when waiting for the start of an MLLP frame
     *
     * @param receiveTimeout timeout in milliseconds
     */
    public void setReceiveTimeout(int receiveTimeout) {
        this.receiveTimeout = receiveTimeout;
    }

    public int getMaxReceiveTimeouts() {
        return maxReceiveTimeouts;
    }

    /**
     * The maximum number of timeouts (specified by receiveTimeout) allowed before the TCP Connection will be reset.
     *
     * @param maxReceiveTimeouts maximum number of receiveTimeouts
     */
    public void setMaxReceiveTimeouts(int maxReceiveTimeouts) {
        this.maxReceiveTimeouts = maxReceiveTimeouts;
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    /**
     * The SO_TIMEOUT value (in milliseconds) used after the start of an MLLP frame has been received
     *
     * @param readTimeout timeout in milliseconds
     */
    public void setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
    }

    public boolean isKeepAlive() {
        return keepAlive;
    }

    /**
     * Enable/disable the SO_KEEPALIVE socket option.
     *
     * @param keepAlive enable SO_KEEPALIVE when true; otherwise disable SO_KEEPALIVE
     */
    public void setKeepAlive(boolean keepAlive) {
        this.keepAlive = keepAlive;
    }

    public boolean isTcpNoDelay() {
        return tcpNoDelay;
    }

    /**
     * Enable/disable the TCP_NODELAY socket option.
     *
     * @param tcpNoDelay enable TCP_NODELAY when true; otherwise disable TCP_NODELAY
     */
    public void setTcpNoDelay(boolean tcpNoDelay) {
        this.tcpNoDelay = tcpNoDelay;
    }

    public boolean isReuseAddress() {
        return reuseAddress;
    }

    /**
     * Enable/disable the SO_REUSEADDR socket option.
     *
     * @param reuseAddress enable SO_REUSEADDR when true; otherwise disable SO_REUSEADDR
     */
    public void setReuseAddress(boolean reuseAddress) {
        this.reuseAddress = reuseAddress;
    }

    public int getReceiveBufferSize() {
        return receiveBufferSize;
    }

    /**
     * Sets the SO_RCVBUF option to the specified value (in bytes)
     *
     * @param receiveBufferSize the SO_RCVBUF option value.  If null, the system default is used
     */
    public void setReceiveBufferSize(Integer receiveBufferSize) {
        this.receiveBufferSize = receiveBufferSize;
    }

    public int getSendBufferSize() {
        return sendBufferSize;
    }

    /**
     * Sets the SO_SNDBUF option to the specified value (in bytes)
     *
     * @param sendBufferSize the SO_SNDBUF option value.  If null, the system default is used
     */
    public void setSendBufferSize(Integer sendBufferSize) {
        this.sendBufferSize = sendBufferSize;
    }

    public boolean isAutoAck() {
        return autoAck;
    }

    /**
     * Enable/Disable the automatic generation of a MLLP Acknowledgement
     *
     * MLLP Consumers only
     *
     * @param autoAck enabled if true, otherwise disabled
     */
    public void setAutoAck(boolean autoAck) {
        this.autoAck = autoAck;
    }

    public boolean isHl7Headers() {
        return hl7Headers;
    }

    /**
     * Enable/Disable the automatic generation of message headers from the HL7 Message
     *
     * MLLP Consumers only
     *
     * @param hl7Headers enabled if true, otherwise disabled
     */
    public void setHl7Headers(boolean hl7Headers) {
        this.hl7Headers = hl7Headers;
    }

    public boolean isValidatePayload() {
        return validatePayload;
    }

    /**
     * Enable/Disable the validation of HL7 Payloads
     *
     * If enabled, HL7 Payloads received from external systems will be validated (see Hl7Util.generateInvalidPayloadExceptionMessage for details on the validation).
     * If and invalid payload is detected, a MllpInvalidMessageException (for consumers) or a MllpInvalidAcknowledgementException will be thrown.
     *
     * @param validatePayload enabled if true, otherwise disabled
     */
    public void setValidatePayload(boolean validatePayload) {
        this.validatePayload = validatePayload;
    }

    public boolean isBufferWrites() {
        return bufferWrites;
    }

    /**
     * Enable/Disable the validation of HL7 Payloads
     *
     * If enabled, MLLP Payloads are buffered and written to the external system in a single write(byte[]) operation.
     * If disabled, the MLLP payload will not be buffered, and three write operations will be used.  The first operation
     * will write the MLLP start-of-block character {0x0b (ASCII VT)}, the second operation will write the HL7 payload, and the
     * third operation will writh the MLLP end-of-block character and the MLLP end-of-data character {[0x1c, 0x0d] (ASCII [FS, CR])}.
     *
     * @param bufferWrites enabled if true, otherwise disabled
     */
    public void setBufferWrites(boolean bufferWrites) {
        this.bufferWrites = bufferWrites;
    }
}
