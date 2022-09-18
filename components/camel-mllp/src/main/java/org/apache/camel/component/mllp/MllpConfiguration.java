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

import java.util.Objects;

import org.apache.camel.ExchangePattern;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The MLLP Component configuration.
 */
@UriParams
public class MllpConfiguration implements Cloneable {
    static final Logger LOG = LoggerFactory.getLogger(MllpConfiguration.class);

    // URI Parameters overridden from DefaultEndpoint
    @UriParam(label = "consumer", defaultValue = "true")
    boolean bridgeErrorHandler = true;

    @UriParam(label = "consumer,advanced", defaultValue = "InOut")
    ExchangePattern exchangePattern = ExchangePattern.InOut;

    // camel-mllp specific URI parameters
    @UriParam(label = "advanced,consumer", defaultValue = "5")
    Integer backlog = 5;

    @UriParam(label = "advanced,consumer", defaultValue = "30000")
    int bindTimeout = 30000;

    @UriParam(label = "advanced,consumer", defaultValue = "5000")
    int bindRetryInterval = 5000;

    @UriParam(label = "advanced,consumer", defaultValue = "false")
    boolean lenientBind;

    @UriParam(label = "advanced,consumer", defaultValue = "60000")
    int acceptTimeout = 60000;

    @UriParam(label = "advanced,producer", defaultValue = "30000")
    int connectTimeout = 30000;

    @UriParam(label = "advanced", defaultValue = "15000")
    int receiveTimeout = 15000;

    @UriParam(label = "advanced,consumer", defaultValue = "5")
    int maxConcurrentConsumers = 5;

    @UriParam(label = "advanced,tcp", defaultValue = "null")
    Integer idleTimeout;

    @UriParam(label = "advanced", defaultValue = "5000")
    int readTimeout = 5000;

    @UriParam(label = "advanced,producer", defaultValue = "true")
    Boolean keepAlive = true;

    @UriParam(label = "advanced,producer", defaultValue = "true")
    Boolean tcpNoDelay = true;

    @UriParam(label = "advanced,consumer", defaultValue = "false")
    Boolean reuseAddress = false;

    @UriParam(label = "advanced", defaultValue = "8192")
    Integer receiveBufferSize = 8192;

    @UriParam(label = "advanced", defaultValue = "8192")
    Integer sendBufferSize = 8192;

    @UriParam(defaultValue = "true")
    boolean autoAck = true;

    @UriParam(defaultValue = "true")
    boolean hl7Headers = true;

    @UriParam(defaultValue = "true")
    boolean requireEndOfData = true;

    @UriParam(defaultValue = "true")
    boolean stringPayload = true;

    @UriParam(defaultValue = "false")
    boolean validatePayload;

    @UriParam
    String charsetName;

    @UriParam(label = "advanced,producer", defaultValue = "RESET")
    MllpIdleTimeoutStrategy idleTimeoutStrategy = MllpIdleTimeoutStrategy.RESET;

    @UriParam(label = "advanced", defaultValue = "2048")
    int minBufferSize = 2048;
    @UriParam(label = "advanced", defaultValue = "" + 0x40000000)
    int maxBufferSize = 0x40000000;

    public MllpConfiguration() {
    }

    public static void copy(MllpConfiguration source, MllpConfiguration target) {
        if (source == null) {
            LOG.warn(
                    "Values were not copied by MllpConfiguration.copy(MllpConfiguration source, MllpConfiguration target) - source argument is null");
        } else if (target == null) {
            LOG.warn(
                    "Values were not copied by MllpConfiguration.copy(MllpConfiguration source, MllpConfiguration target) - target argument is null");
        } else {
            target.bridgeErrorHandler = source.bridgeErrorHandler;
            target.exchangePattern = source.exchangePattern;

            target.backlog = source.backlog;
            target.bindTimeout = source.bindTimeout;
            target.bindRetryInterval = source.bindRetryInterval;
            target.acceptTimeout = source.acceptTimeout;
            target.connectTimeout = source.connectTimeout;
            target.receiveTimeout = source.receiveTimeout;
            target.idleTimeout = source.idleTimeout;
            target.idleTimeoutStrategy = source.idleTimeoutStrategy;
            target.readTimeout = source.readTimeout;
            target.keepAlive = source.keepAlive;
            target.tcpNoDelay = source.tcpNoDelay;
            target.reuseAddress = source.reuseAddress;
            target.receiveBufferSize = source.receiveBufferSize;
            target.sendBufferSize = source.sendBufferSize;
            target.autoAck = source.autoAck;
            target.hl7Headers = source.hl7Headers;
            target.requireEndOfData = source.requireEndOfData;
            target.stringPayload = source.stringPayload;
            target.validatePayload = source.validatePayload;
            target.charsetName = source.charsetName;
            target.maxConcurrentConsumers = source.maxConcurrentConsumers;
        }
    }

    public MllpConfiguration copy() {
        MllpConfiguration target = new MllpConfiguration();

        MllpConfiguration.copy(this, target);

        return target;
    }

    public void copy(MllpConfiguration source) {
        MllpConfiguration.copy(source, this);
    }

    public boolean isBridgeErrorHandler() {
        return bridgeErrorHandler;
    }

    /**
     * Allows for bridging the consumer to the Camel routing Error Handler, which mean any exceptions occurred while the
     * consumer is trying to receive incoming messages, or the likes, will now be processed as a message and handled by
     * the routing Error Handler.
     *
     * If disabled, the consumer will use the org.apache.camel.spi.ExceptionHandler to deal with exceptions by logging
     * them at WARN or ERROR level and ignored.
     */
    public void setBridgeErrorHandler(boolean bridgeErrorHandler) {
        this.bridgeErrorHandler = bridgeErrorHandler;
    }

    public ExchangePattern getExchangePattern() {
        return exchangePattern;
    }

    /**
     * Sets the exchange pattern when the consumer creates an exchange.
     */
    public void setExchangePattern(ExchangePattern exchangePattern) {
        this.exchangePattern = exchangePattern;
    }

    public boolean hasCharsetName() {
        return charsetName != null && !charsetName.isEmpty();
    }

    public String getCharsetName() {
        return charsetName;
    }

    /**
     * Sets the default charset to use
     */
    public void setCharsetName(String charsetName) {
        this.charsetName = charsetName;
    }

    public boolean hasBacklog() {
        return backlog != null && backlog > 0;
    }

    public Integer getBacklog() {
        return backlog;
    }

    /**
     * The maximum queue length for incoming connection indications (a request to connect) is set to the backlog
     * parameter. If a connection indication arrives when the queue is full, the connection is refused.
     */
    public void setBacklog(Integer backlog) {
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

    public boolean isLenientBind() {
        return lenientBind;
    }

    /**
     * TCP Server Only - Allow the endpoint to start before the TCP ServerSocket is bound.
     *
     * In some environments, it may be desirable to allow the endpoint to start before the TCP ServerSocket is bound.
     *
     * @param lenientBind if true, the ServerSocket will be bound asynchronously; otherwise the ServerSocket will be
     *                    bound synchronously.
     */
    public void setLenientBind(boolean lenientBind) {
        this.lenientBind = lenientBind;
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

    public int getMaxConcurrentConsumers() {
        return maxConcurrentConsumers;
    }

    /**
     * The maximum number of concurrent MLLP Consumer connections that will be allowed. If a new connection is received
     * and the maximum is number are already established, the new connection will be reset immediately.
     *
     * @param maxConcurrentConsumers the maximum number of concurrent consumer connections allowed
     */
    public void setMaxConcurrentConsumers(int maxConcurrentConsumers) {
        this.maxConcurrentConsumers = maxConcurrentConsumers;
    }

    public boolean hasIdleTimeout() {
        return idleTimeout != null && idleTimeout > 0;
    }

    public Integer getIdleTimeout() {
        return idleTimeout;
    }

    /**
     * The approximate idle time allowed before the Client TCP Connection will be reset.
     *
     * A null value or a value less than or equal to zero will disable the idle timeout.
     *
     * @param idleTimeout timeout in milliseconds
     */
    public void setIdleTimeout(Integer idleTimeout) {
        this.idleTimeout = idleTimeout;
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

    public boolean hasKeepAlive() {
        return keepAlive != null;
    }

    public Boolean getKeepAlive() {
        return keepAlive;
    }

    /**
     * Enable/disable the SO_KEEPALIVE socket option.
     *
     * @param keepAlive enable SO_KEEPALIVE when true; disable SO_KEEPALIVE when false; use system default when null
     */
    public void setKeepAlive(Boolean keepAlive) {
        this.keepAlive = keepAlive;
    }

    public boolean hasTcpNoDelay() {
        return tcpNoDelay != null;
    }

    public Boolean getTcpNoDelay() {
        return tcpNoDelay;
    }

    /**
     * Enable/disable the TCP_NODELAY socket option.
     *
     * @param tcpNoDelay enable TCP_NODELAY when true; disable TCP_NODELAY when false; use system default when null
     */
    public void setTcpNoDelay(Boolean tcpNoDelay) {
        this.tcpNoDelay = tcpNoDelay;
    }

    public boolean hasReuseAddress() {
        return reuseAddress != null;
    }

    public Boolean getReuseAddress() {
        return reuseAddress;
    }

    /**
     * Enable/disable the SO_REUSEADDR socket option.
     *
     * @param reuseAddress enable SO_REUSEADDR when true; disable SO_REUSEADDR when false; use system default when null
     */
    public void setReuseAddress(Boolean reuseAddress) {
        this.reuseAddress = reuseAddress;
    }

    public boolean hasReceiveBufferSize() {
        return receiveBufferSize != null && receiveBufferSize > 0;
    }

    public int getReceiveBufferSize() {
        return receiveBufferSize;
    }

    /**
     * Sets the SO_RCVBUF option to the specified value (in bytes)
     *
     * @param receiveBufferSize the SO_RCVBUF option value. If null, the system default is used
     */
    public void setReceiveBufferSize(Integer receiveBufferSize) {
        this.receiveBufferSize = receiveBufferSize;
    }

    public boolean hasSendBufferSize() {
        return sendBufferSize != null && sendBufferSize > 0;
    }

    public int getSendBufferSize() {
        return sendBufferSize;
    }

    /**
     * Sets the SO_SNDBUF option to the specified value (in bytes)
     *
     * @param sendBufferSize the SO_SNDBUF option value. If null, the system default is used
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

    public boolean getHl7Headers() {
        return isHl7Headers();
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

    public boolean isRequireEndOfData() {
        return requireEndOfData;
    }

    /**
     * Enable/Disable strict compliance to the MLLP standard.
     *
     * The MLLP standard specifies [START_OF_BLOCK]hl7 payload[END_OF_BLOCK][END_OF_DATA], however, some systems do not
     * send the final END_OF_DATA byte. This setting controls whether or not the final END_OF_DATA byte is required or
     * optional.
     *
     * @param requireEndOfData the trailing END_OF_DATA byte is required if true; optional otherwise
     */
    public void setRequireEndOfData(boolean requireEndOfData) {
        this.requireEndOfData = requireEndOfData;
    }

    public boolean isStringPayload() {
        return stringPayload;
    }

    /**
     * Enable/Disable converting the payload to a String.
     *
     * If enabled, HL7 Payloads received from external systems will be validated converted to a String.
     *
     * If the charsetName property is set, that character set will be used for the conversion. If the charsetName
     * property is not set, the value of MSH-18 will be used to determine th appropriate character set. If MSH-18 is not
     * set, then the default ISO-8859-1 character set will be use.
     *
     * @param stringPayload enabled if true, otherwise disabled
     */
    public void setStringPayload(boolean stringPayload) {
        this.stringPayload = stringPayload;
    }

    public boolean isValidatePayload() {
        return validatePayload;
    }

    /**
     * Enable/Disable the validation of HL7 Payloads
     *
     * If enabled, HL7 Payloads received from external systems will be validated (see
     * Hl7Util.generateInvalidPayloadExceptionMessage for details on the validation). If and invalid payload is
     * detected, a MllpInvalidMessageException (for consumers) or a MllpInvalidAcknowledgementException will be thrown.
     *
     * @param validatePayload enabled if true, otherwise disabled
     */
    public void setValidatePayload(boolean validatePayload) {
        this.validatePayload = validatePayload;
    }

    public MllpIdleTimeoutStrategy getIdleTimeoutStrategy() {
        return idleTimeoutStrategy;
    }

    /**
     * decide what action to take when idle timeout occurs. Possible values are :
     *
     * RESET: set SO_LINGER to 0 and reset the socket CLOSE: close the socket gracefully
     *
     * default is RESET.
     *
     * @param idleTimeoutStrategy the strategy to take if idle timeout occurs
     */
    public void setIdleTimeoutStrategy(MllpIdleTimeoutStrategy idleTimeoutStrategy) {
        this.idleTimeoutStrategy = idleTimeoutStrategy;
    }

    public int getMinBufferSize() {
        return minBufferSize;
    }

    /**
     * Minimum buffer size used when receiving or sending data over the wire.
     */
    public void setMinBufferSize(int minBufferSize) {
        this.minBufferSize = minBufferSize;
    }

    /**
     * Maximum buffer size used when receiving or sending data over the wire.
     */
    public int getMaxBufferSize() {
        return maxBufferSize;
    }

    public void setMaxBufferSize(int maxBufferSize) {
        this.maxBufferSize = maxBufferSize;
    }

    @Override
    public int hashCode() {
        return Objects.hash(bridgeErrorHandler,
                exchangePattern,
                backlog,
                bindTimeout,
                bindRetryInterval,
                acceptTimeout,
                connectTimeout,
                receiveTimeout,
                maxConcurrentConsumers,
                idleTimeout,
                idleTimeoutStrategy,
                readTimeout,
                keepAlive,
                tcpNoDelay,
                reuseAddress,
                receiveBufferSize,
                sendBufferSize,
                autoAck,
                hl7Headers,
                requireEndOfData,
                stringPayload,
                validatePayload,
                charsetName,
                minBufferSize,
                maxBufferSize);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof MllpConfiguration)) {
            return false;
        }

        MllpConfiguration rhs = (MllpConfiguration) o;

        return bridgeErrorHandler == rhs.bridgeErrorHandler
                && exchangePattern == rhs.exchangePattern
                && bindTimeout == rhs.bindTimeout
                && bindRetryInterval == rhs.bindRetryInterval
                && acceptTimeout == rhs.acceptTimeout
                && connectTimeout == rhs.connectTimeout
                && receiveTimeout == rhs.receiveTimeout
                && readTimeout == rhs.readTimeout
                && autoAck == rhs.autoAck
                && hl7Headers == rhs.hl7Headers
                && requireEndOfData == rhs.requireEndOfData
                && stringPayload == rhs.stringPayload
                && validatePayload == rhs.validatePayload
                && idleTimeoutStrategy == rhs.idleTimeoutStrategy
                && minBufferSize == rhs.minBufferSize
                && maxBufferSize == rhs.maxBufferSize
                && Objects.equals(backlog, rhs.backlog)
                && Objects.equals(maxConcurrentConsumers, rhs.maxConcurrentConsumers)
                && Objects.equals(idleTimeout, rhs.idleTimeout)
                && Objects.equals(keepAlive, rhs.keepAlive)
                && Objects.equals(tcpNoDelay, rhs.tcpNoDelay)
                && Objects.equals(reuseAddress, rhs.reuseAddress)
                && Objects.equals(receiveBufferSize, rhs.receiveBufferSize)
                && Objects.equals(sendBufferSize, rhs.sendBufferSize)
                && Objects.equals(charsetName, rhs.charsetName);
    }

    @Override
    public String toString() {
        return "MllpConfiguration{"
               + "bridgeErrorHandler=" + bridgeErrorHandler
               + ", exchangePattern=" + exchangePattern
               + ", backlog=" + backlog
               + ", bindTimeout=" + bindTimeout
               + ", bindRetryInterval=" + bindRetryInterval
               + ", acceptTimeout=" + acceptTimeout
               + ", connectTimeout=" + connectTimeout
               + ", receiveTimeout=" + receiveTimeout
               + ", maxConcurrentConsumers=" + maxConcurrentConsumers
               + ", idleTimeout=" + idleTimeout
               + ", idleTimeoutStrategy=" + idleTimeoutStrategy
               + ", readTimeout=" + readTimeout
               + ", keepAlive=" + keepAlive
               + ", tcpNoDelay=" + tcpNoDelay
               + ", reuseAddress=" + reuseAddress
               + ", receiveBufferSize=" + receiveBufferSize
               + ", sendBufferSize=" + sendBufferSize
               + ", autoAck=" + autoAck
               + ", hl7Headers=" + hl7Headers
               + ", requireEndOfData=" + requireEndOfData
               + ", stringPayload=" + stringPayload
               + ", validatePayload=" + validatePayload
               + ", minBufferSize=" + minBufferSize
               + ", maxBufferSize=" + maxBufferSize
               + ", charsetName='" + charsetName + '\''
               + '}';
    }
}
