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

import java.util.Objects;

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

    @UriParam(label = "advanced,consumer,tcp", defaultValue = "5")
    Integer backlog = 5;

    @UriParam(label = "advanced,consumer,tcp,timeout", defaultValue = "30000")
    int bindTimeout = 30000;

    @UriParam(label = "advanced,consumer,tcp,timeout", defaultValue = "5000")
    int bindRetryInterval = 5000;

    @UriParam(label = "advanced,consumer,tcp,timeout", defaultValue = "60000")
    int acceptTimeout = 60000;

    @UriParam(label = "advanced,producer,tcp,timeout", defaultValue = "30000")
    int connectTimeout = 30000;

    @UriParam(label = "advanced,tcp,timeout", defaultValue = "15000")
    int receiveTimeout = 15000;

    @UriParam(label = "advanced,consumer,tcp", defaultValue = "5")
    int maxConcurrentConsumers = 5;

    @Deprecated // use idleTimeout
    @UriParam(label = "advanced,consumer,tcp,timeout", defaultValue = "null")
    Integer maxReceiveTimeouts;

    @UriParam(label = "advanced,tcp,timeout", defaultValue = "null")
    Integer idleTimeout;

    @UriParam(label = "advanced,tcp,timeout", defaultValue = "500")
    int readTimeout = 500;

    @UriParam(label = "advanced,producer,tcp", defaultValue = "true")
    Boolean keepAlive = true;

    @UriParam(label = "advanced,producer,tcp", defaultValue = "true")
    Boolean tcpNoDelay = true;

    @UriParam(label = "advanced,consumer,tcp", defaultValue = "true")
    Boolean reuseAddress = true;

    @UriParam(label = "advanced,tcp", defaultValue = "8192")
    Integer receiveBufferSize = 8192;

    @UriParam(label = "advanced,tcp", defaultValue = "8192")
    Integer sendBufferSize = 8192;

    @UriParam(defaultValue = "true")
    boolean autoAck = true;

    @UriParam(defaultValue = "true")
    boolean hl7Headers = true;

    @UriParam(defaultValue = "false")
    @Deprecated
    boolean bufferWrites;

    @UriParam(defaultValue = "true")
    boolean requireEndOfData = true;

    @UriParam(defaultValue = "true")
    boolean stringPayload = true;

    @UriParam(defaultValue = "false")
    boolean validatePayload;

    @UriParam(label = "codec")
    String charsetName;

    public MllpConfiguration() {
    }

    public MllpConfiguration(MllpConfiguration source) {
        this.copy(source);
    }

    public static void copy(MllpConfiguration source, MllpConfiguration target) {
        if (source == null) {
            LOG.warn("Values were not copied by MllpConfiguration.copy(MllpConfiguration source, MllpConfiguration target) - source argument is null");
        } else if (target == null) {
            LOG.warn("Values were not copied by MllpConfiguration.copy(MllpConfiguration source, MllpConfiguration target) - target argument is null");
        } else {
            target.backlog = source.backlog;
            target.bindTimeout = source.bindTimeout;
            target.bindRetryInterval = source.bindRetryInterval;
            target.acceptTimeout = source.acceptTimeout;
            target.connectTimeout = source.connectTimeout;
            target.receiveTimeout = source.receiveTimeout;
            target.idleTimeout = source.idleTimeout;
            target.readTimeout = source.readTimeout;
            target.keepAlive = source.keepAlive;
            target.tcpNoDelay = source.tcpNoDelay;
            target.reuseAddress = source.reuseAddress;
            target.receiveBufferSize = source.receiveBufferSize;
            target.sendBufferSize = source.sendBufferSize;
            target.autoAck = source.autoAck;
            target.hl7Headers = source.hl7Headers;
            target.bufferWrites = source.bufferWrites;
            target.requireEndOfData = source.requireEndOfData;
            target.stringPayload = source.stringPayload;
            target.validatePayload = source.validatePayload;
            target.charsetName = source.charsetName;
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

    public boolean hasCharsetName() {
        return charsetName != null && !charsetName.isEmpty();
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

    public boolean hasBacklog() {
        return backlog != null && backlog > 0;
    }

    public Integer getBacklog() {
        return backlog;
    }

    /**
     * The maximum queue length for incoming connection indications (a request to connect) is set to the backlog parameter. If a connection indication arrives when the queue is full, the connection is
     * refused.
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
     * The maximum number of concurrent MLLP Consumer connections that will be allowed.  If a new
     * connection is received and the maximum is number are already established, the new connection will be reset immediately.
     *
     * @param maxConcurrentConsumers the maximum number of concurrent consumer connections allowed
     */
    public void setMaxConcurrentConsumers(int maxConcurrentConsumers) {
        this.maxConcurrentConsumers = maxConcurrentConsumers;
    }

    /**
     * Determine if the maxReceiveTimeouts URI parameter has been set
     *
     * @return true if the parameter has been set; false otherwise
     *
     * @deprecated Use the idleTimeout URI parameter
     */
    public boolean hasMaxReceiveTimeouts() {
        return maxReceiveTimeouts != null;
    }

    /**
     * Retrieve the value of the maxReceiveTimeouts URI parameter.
     *
     * @return the maximum number of receive timeouts before the TCP Socket is reset
     *
     * @deprecated Use the idleTimeout URI parameter
     */
    public Integer getMaxReceiveTimeouts() {
        return maxReceiveTimeouts;
    }

    /**
     * The maximum number of timeouts (specified by receiveTimeout) allowed before the TCP Connection will be reset.
     *
     * @param maxReceiveTimeouts maximum number of receiveTimeouts
     *
     * @deprecated Use the idleTimeout URI parameter.  For backward compibility, setting this parameter will result in an
     * idle timeout of maxReceiveTimeouts * receiveTimeout.  If idleTimeout is also specified, this parameter will be ignored.
     */
    public void setMaxReceiveTimeouts(Integer maxReceiveTimeouts) {
        this.maxReceiveTimeouts = maxReceiveTimeouts;
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
    public void setReuseAddress(boolean reuseAddress) {
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
     * @param receiveBufferSize the SO_RCVBUF option value.  If null, the system default is used
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
     * Enable disable strict compliance to the MLLP standard.
     *
     * The MLLP standard specifies [START_OF_BLOCK]hl7 payload[END_OF_BLOCK][END_OF_DATA], however, some systems do not send
     * the final END_OF_DATA byte.  This setting controls whether or not the final END_OF_DATA byte is required or optional.
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
     * If the charsetName property is set, that character set will be used for the conversion.  If the charsetName property is
     * not set, the value of MSH-18 will be used to determine th appropriate character set.  If MSH-18 is not set, then
     * the default ASCII character set will be use.
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
     * If enabled, HL7 Payloads received from external systems will be validated (see Hl7Util.generateInvalidPayloadExceptionMessage
     * for details on the validation). If and invalid payload is detected, a MllpInvalidMessageException (for consumers) or
     * a MllpInvalidAcknowledgementException will be thrown.
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
     * @deprecated the parameter will be ignored
     *
     * @param bufferWrites enabled if true, otherwise disabled
     */
    public void setBufferWrites(boolean bufferWrites) {
        this.bufferWrites = bufferWrites;
    }

    @Override
    public int hashCode() {
        return Objects.hash(backlog,
            bindTimeout,
            bindRetryInterval,
            acceptTimeout,
            connectTimeout,
            receiveTimeout,
            maxConcurrentConsumers,
            maxReceiveTimeouts,
            idleTimeout,
            readTimeout,
            keepAlive,
            tcpNoDelay,
            reuseAddress,
            receiveBufferSize,
            sendBufferSize,
            autoAck,
            hl7Headers,
            bufferWrites,
            requireEndOfData,
            stringPayload,
            validatePayload,
            charsetName);
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

        return bindTimeout == rhs.bindTimeout
            && bindRetryInterval == rhs.bindRetryInterval
            && acceptTimeout == rhs.acceptTimeout
            && connectTimeout == rhs.connectTimeout
            && receiveTimeout == rhs.receiveTimeout
            && readTimeout == rhs.readTimeout
            && autoAck == rhs.autoAck
            && hl7Headers == rhs.hl7Headers
            && bufferWrites == rhs.bufferWrites
            && requireEndOfData == rhs.requireEndOfData
            && stringPayload == rhs.stringPayload
            && validatePayload == rhs.validatePayload
            && Objects.equals(backlog, rhs.backlog)
            && Objects.equals(maxConcurrentConsumers, rhs.maxConcurrentConsumers)
            && Objects.equals(maxReceiveTimeouts, rhs.maxReceiveTimeouts)
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
            + "backlog=" + backlog
            + ", bindTimeout=" + bindTimeout
            + ", bindRetryInterval=" + bindRetryInterval
            + ", acceptTimeout=" + acceptTimeout
            + ", connectTimeout=" + connectTimeout
            + ", receiveTimeout=" + receiveTimeout
            + ", maxConcurrentConsumers=" + maxConcurrentConsumers
            + ", maxReceiveTimeouts=" + maxReceiveTimeouts
            + ", idleTimeout=" + idleTimeout
            + ", readTimeout=" + readTimeout
            + ", keepAlive=" + keepAlive
            + ", tcpNoDelay=" + tcpNoDelay
            + ", reuseAddress=" + reuseAddress
            + ", receiveBufferSize=" + receiveBufferSize
            + ", sendBufferSize=" + sendBufferSize
            + ", autoAck=" + autoAck
            + ", hl7Headers=" + hl7Headers
            + ", bufferWrites=" + bufferWrites
            + ", requireEndOfData=" + requireEndOfData
            + ", stringPayload=" + stringPayload
            + ", validatePayload=" + validatePayload
            + ", charsetName='" + charsetName + '\''
            + '}';
    }
}
