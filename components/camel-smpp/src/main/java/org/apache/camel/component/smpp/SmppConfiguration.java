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
package org.apache.camel.component.smpp;

import java.net.URI;
import java.nio.charset.Charset;
import java.util.Map;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.jsmpp.bean.Alphabet;
import org.jsmpp.bean.NumberingPlanIndicator;
import org.jsmpp.bean.ReplaceIfPresentFlag;
import org.jsmpp.bean.SMSCDeliveryReceipt;
import org.jsmpp.bean.TypeOfNumber;
import org.jsmpp.session.SessionStateListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Contains the SMPP component configuration properties</a>
 */
@UriParams
public class SmppConfiguration implements Cloneable {
    private static final Logger LOG = LoggerFactory.getLogger(SmppConfiguration.class);

    @UriPath(defaultValue = "localhost")
    private String host = "localhost";
    @UriPath(defaultValue = "2775")
    private Integer port = 2775;
    @UriParam(label = "security", defaultValue = "smppclient", secret = true)
    private String systemId = "smppclient";
    @UriParam(label = "security", secret = true)
    private String password;
    @UriParam(label = "common", defaultValue = "")
    private String systemType = "";
    @UriParam(label = "codec")
    private byte dataCoding = (byte) 0;
    @UriParam(label = "codec", enums = "0,4,8")
    private byte alphabet = Alphabet.ALPHA_DEFAULT.value();
    @UriParam(label = "codec", defaultValue = "ISO-8859-1")
    private String encoding = "ISO-8859-1";
    @UriParam(label = "advanced", defaultValue = "5000")
    private Integer enquireLinkTimer = 5000;
    @UriParam(label = "advanced", defaultValue = "10000")
    private Integer transactionTimer = 10000;
    @UriParam(label = "producer", enums = "0,1,2")
    private byte registeredDelivery = SMSCDeliveryReceipt.SUCCESS_FAILURE.value();
    @UriParam(label = "producer", defaultValue = "", enums = "CMT,CPT,VMN,VMA,WAP,USSD")
    private String serviceType = "";
    @UriParam(label = "producer", defaultValue = "1616")
    private String sourceAddr = "1616";
    @UriParam(label = "producer", defaultValue = "1717")
    private String destAddr = "1717";
    @UriParam(label = "producer", enums = "0,1,2,3,4,5,6")
    private byte sourceAddrTon = TypeOfNumber.UNKNOWN.value();
    @UriParam(label = "producer", enums = "0,1,2,3,4,5,6")
    private byte destAddrTon = TypeOfNumber.UNKNOWN.value();
    @UriParam(label = "producer", enums = "0,1,2,3,6,8,9,10,13,18")
    private byte sourceAddrNpi = NumberingPlanIndicator.UNKNOWN.value();
    @UriParam(label = "producer", enums = "0,1,2,3,6,8,9,10,13,18")
    private byte destAddrNpi = NumberingPlanIndicator.UNKNOWN.value();
    @UriParam(label = "consumer")
    private String addressRange = "";
    @UriParam(label = "producer")
    private byte protocolId = (byte) 0;
    @UriParam(label = "producer", enums = "0,1,2,3")
    private byte priorityFlag = (byte) 1;
    @UriParam(label = "producer", enums = "0,1")
    private byte replaceIfPresentFlag = ReplaceIfPresentFlag.DEFAULT.value();
    @UriParam(label = "producer", enums = "0,1,2,3,4,5,6")
    private byte typeOfNumber = TypeOfNumber.UNKNOWN.value();
    @UriParam(label = "producer", enums = "0,1,2,3,6,8,9,10,13,18")
    private byte numberingPlanIndicator = NumberingPlanIndicator.UNKNOWN.value();
    @UriParam(label = "security")
    private boolean usingSSL;
    @UriParam(label = "common", defaultValue = "5000")
    private long initialReconnectDelay = 5000;
    @UriParam(label = "common", defaultValue = "5000")
    private long reconnectDelay = 5000;
    @UriParam(label = "common", defaultValue = "2147483647")
    private int maxReconnect = Integer.MAX_VALUE;
    @UriParam(label = "producer")
    private boolean lazySessionCreation;
    @UriParam(label = "proxy")
    private String httpProxyHost;
    @UriParam(label = "proxy", defaultValue = "3128")
    private Integer httpProxyPort = 3128;
    @UriParam(label = "proxy")
    private String httpProxyUsername;
    @UriParam(label = "proxy")
    private String httpProxyPassword;
    @UriParam(label = "proxy")
    private Map<String, String> proxyHeaders;
    @UriParam(label = "advanced")
    private SessionStateListener sessionStateListener;
    @UriParam(defaultValue = "ALLOW")
    private SmppSplittingPolicy splittingPolicy = SmppSplittingPolicy.ALLOW;

    /**
     * A POJO which contains all necessary configuration parameters for the SMPP connection
     *
     * @param uri the full URI of the endpoint
     */
    public void configureFromURI(URI uri) {
        String userInfo = uri.getUserInfo();
        if (userInfo != null) {
            setSystemId(uri.getUserInfo());
        }

        String host = uri.getHost();
        if (host != null) {
            setHost(host);
        }

        int port = uri.getPort();
        if (port > 0) {
            setPort(port);
        }

        if (uri.getScheme().startsWith("smpps")) {
            setUsingSSL(true);
        }
    }

    public SmppConfiguration copy() {
        try {
            return (SmppConfiguration) clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

    public String getHost() {
        return host;
    }

    /**
     * Hostname for the SMSC server to use.
     */
    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    /**
     * Port number for the SMSC server to use.
     */
    public void setPort(Integer port) {
        this.port = port;
    }

    public String getSystemId() {
        return systemId;
    }

    /**
     * The system id (username) for connecting to SMSC server.
     */
    public void setSystemId(String systemId) {
        this.systemId = systemId;
    }

    /**
     * The password for connecting to SMSC server.
     */
    public String getPassword() {
        return password;
    }

    public byte getDataCoding() {
        return dataCoding;
    }

    /**
     * Defines the data coding according the SMPP 3.4 specification, section 5.2.19.
     * Example data encodings are:
     * <ul>
     *     <li>0: SMSC Default Alphabet</li>
     *     <li>3: Latin 1 (ISO-8859-1)</li>
     *     <li>4: Octet unspecified (8-bit binary)</li>
     *     <li>8: UCS2 (ISO/IEC-10646)</li>
     *     <li>13: Extended Kanji JIS(X 0212-1990)</li>
     * </ul>
     */
    public void setDataCoding(byte dataCoding) {
        this.dataCoding = dataCoding;
    }

    public byte getAlphabet() {
        return alphabet;
    }

    /**
     * Defines encoding of data according the SMPP 3.4 specification, section 5.2.19.
     * <ul>
     *     <li>0: SMSC Default Alphabet
     *     <li>4: 8 bit Alphabet</li>
     *     <li>8: UCS2 Alphabet</li></li>
     * </ul>
     */
    public void setAlphabet(byte alphabet) {
        this.alphabet = alphabet;
    }

    public String getEncoding() {
        return encoding;
    }

    /**
     * Defines the encoding scheme of the short message user data.
     * Only for SubmitSm, ReplaceSm and SubmitMulti.
     */
    public void setEncoding(String encoding) {
        if (!Charset.isSupported(encoding)) {
            LOG.warn("Unsupported encoding \"{}\" is being set.", encoding);
        }
        this.encoding = encoding;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Integer getEnquireLinkTimer() {
        return enquireLinkTimer;
    }

    /**
     * Defines the interval in milliseconds between the confidence checks.
     * The confidence check is used to test the communication path between an ESME and an SMSC.
     */
    public void setEnquireLinkTimer(Integer enquireLinkTimer) {
        this.enquireLinkTimer = enquireLinkTimer;
    }

    public Integer getTransactionTimer() {
        return transactionTimer;
    }

    /**
     * Defines the maximum period of inactivity allowed after a transaction, after which
     * an SMPP entity may assume that the session is no longer active.
     * This timer may be active on either communicating SMPP entity (i.e. SMSC or ESME).
     */
    public void setTransactionTimer(Integer transactionTimer) {
        this.transactionTimer = transactionTimer;
    }

    public String getSystemType() {
        return systemType;
    }

    /**
     * This parameter is used to categorize the type of ESME (External Short Message Entity) that is binding to the SMSC (max. 13 characters).
     */
    public void setSystemType(String systemType) {
        this.systemType = systemType;
    }

    public byte getRegisteredDelivery() {
        return registeredDelivery;
    }

    /**
     * Is used to request an SMSC delivery receipt and/or SME originated acknowledgements. The following values are defined:
     * <ul>
     *     <li>0: No SMSC delivery receipt requested.</li>
     *     <li>1: SMSC delivery receipt requested where final delivery outcome is success or failure.</li>
     *     <li>2: SMSC delivery receipt requested where the final delivery outcome is delivery failure.</li>
     * </ul>
     */
    public void setRegisteredDelivery(byte registeredDelivery) {
        this.registeredDelivery = registeredDelivery;
    }

    public String getServiceType() {
        return serviceType;
    }

    /**
     * The service type parameter can be used to indicate the SMS Application service associated with the message.
     * The following generic service_types are defined:
     * <ul>
     *     <li>CMT: Cellular Messaging</li>
     *     <li>CPT: Cellular Paging</li>
     *     <li>VMN: Voice Mail Notification</li>
     *     <li>VMA: Voice Mail Alerting</li>
     *     <li>WAP: Wireless Application Protocol</li>
     *     <li>USSD: Unstructured Supplementary Services Data</li>
     * </ul>
     */
    public void setServiceType(String serviceType) {
        this.serviceType = serviceType;
    }

    public byte getSourceAddrTon() {
        return sourceAddrTon;
    }

    /**
     * Defines the type of number (TON) to be used in the SME originator address parameters.
     * The following TON values are defined:
     * <ul>
     *     <li>0: Unknown</li>
     *     <li>1: International</li>
     *     <li>2: National</li>
     *     <li>3: Network Specific</li>
     *     <li>4: Subscriber Number</li>
     *     <li>5: Alphanumeric</li>
     *     <li>6: Abbreviated</li>
     * </ul>
     */
    public void setSourceAddrTon(byte sourceAddrTon) {
        this.sourceAddrTon = sourceAddrTon;
    }

    public byte getDestAddrTon() {
        return destAddrTon;
    }

    /**
     * Defines the type of number (TON) to be used in the SME destination address parameters.
     * Only for SubmitSm, SubmitMulti, CancelSm and DataSm.
     * The following TON values are defined:
     * <ul>
     *     <li>0: Unknown</li>
     *     <li>1: International</li>
     *     <li>2: National</li>
     *     <li>3: Network Specific</li>
     *     <li>4: Subscriber Number</li>
     *     <li>5: Alphanumeric</li>
     *     <li>6: Abbreviated</li>
     * </ul>
     */
    public void setDestAddrTon(byte destAddrTon) {
        this.destAddrTon = destAddrTon;
    }

    public byte getSourceAddrNpi() {
        return sourceAddrNpi;
    }

    /**
     * Defines the numeric plan indicator (NPI) to be used in the SME originator address parameters.
     * The following NPI values are defined:
     * <ul>
     *     <li>0: Unknown</li>
     *     <li>1: ISDN (E163/E164)</li>
     *     <li>2: Data (X.121)</li>
     *     <li>3: Telex (F.69)</li>
     *     <li>6: Land Mobile (E.212)</li>
     *     <li>8: National</li>
     *     <li>9: Private</li>
     *     <li>10: ERMES</li>
     *     <li>13: Internet (IP)</li>
     *     <li>18: WAP Client Id (to be defined by WAP Forum)</li>
     * </ul>
     */
    public void setSourceAddrNpi(byte sourceAddrNpi) {
        this.sourceAddrNpi = sourceAddrNpi;
    }

    public byte getDestAddrNpi() {
        return destAddrNpi;
    }

    /**
     * Defines the type of number (TON) to be used in the SME destination address parameters.
     * Only for SubmitSm, SubmitMulti, CancelSm and DataSm.
     * The following NPI values are defined:
     * <ul>
     *     <li>0: Unknown</li>
     *     <li>1: ISDN (E163/E164)</li>
     *     <li>2: Data (X.121)</li>
     *     <li>3: Telex (F.69)</li>
     *     <li>6: Land Mobile (E.212)</li>
     *     <li>8: National</li>
     *     <li>9: Private</li>
     *     <li>10: ERMES</li>
     *     <li>13: Internet (IP)</li>
     *     <li>18: WAP Client Id (to be defined by WAP Forum)</li>
     * </ul>
     */
    public void setDestAddrNpi(byte destAddrNpi) {
        this.destAddrNpi = destAddrNpi;
    }

    public byte getProtocolId() {
        return protocolId;
    }

    /**
     * The protocol id
     */
    public void setProtocolId(byte protocolId) {
        this.protocolId = protocolId;
    }

    public byte getPriorityFlag() {
        return priorityFlag;
    }

    /**
     * Allows the originating SME to assign a priority level to the short message.
     * Only for SubmitSm and SubmitMulti.
     * Four Priority Levels are supported:
     * <ul>
     *     <li>0: Level 0 (lowest) priority</li>
     *     <li>1: Level 1 priority</li>
     *     <li>2: Level 2 priority</li>
     *     <li>3: Level 3 (highest) priority</li>
     * </ul>
     */
    public void setPriorityFlag(byte priorityFlag) {
        this.priorityFlag = priorityFlag;
    }

    public byte getReplaceIfPresentFlag() {
        return replaceIfPresentFlag;
    }

    /**
     * Used to request the SMSC to replace a previously submitted message, that is still pending delivery.
     * The SMSC will replace an existing message provided that the source address, destination address and service
     * type match the same fields in the new message.
     * The following replace if present flag values are defined:
     * <ul>
     *     <li>0: Don't replace</li>
     *     <li>1: Replace</li>
     * </ul>
     */
    public void setReplaceIfPresentFlag(byte replaceIfPresentFlag) {
        this.replaceIfPresentFlag = replaceIfPresentFlag;
    }

    public String getSourceAddr() {
        return sourceAddr;
    }

    /**
     * Defines the address of SME (Short Message Entity) which originated this message.
     */
    public void setSourceAddr(String sourceAddr) {
        this.sourceAddr = sourceAddr;
    }

    public String getDestAddr() {
        return destAddr;
    }

    /**
     * Defines the destination SME address. For mobile terminated messages, this is the directory number of the recipient MS.
     * Only for SubmitSm, SubmitMulti, CancelSm and DataSm.
     */
    public void setDestAddr(String destAddr) {
        this.destAddr = destAddr;
    }

    public byte getTypeOfNumber() {
        return typeOfNumber;
    }

    /**
     * Defines the type of number (TON) to be used in the SME.
     * The following TON values are defined:
     * <ul>
     *     <li>0: Unknown</li>
     *     <li>1: International</li>
     *     <li>2: National</li>
     *     <li>3: Network Specific</li>
     *     <li>4: Subscriber Number</li>
     *     <li>5: Alphanumeric</li>
     *     <li>6: Abbreviated</li>
     * </ul>
     */
    public void setTypeOfNumber(byte typeOfNumber) {
        this.typeOfNumber = typeOfNumber;
    }

    public byte getNumberingPlanIndicator() {
        return numberingPlanIndicator;
    }

    /**
     * Defines the numeric plan indicator (NPI) to be used in the SME.
     * The following NPI values are defined:
     * <ul>
     *     <li>0: Unknown</li>
     *     <li>1: ISDN (E163/E164)</li>
     *     <li>2: Data (X.121)</li>
     *     <li>3: Telex (F.69)</li>
     *     <li>6: Land Mobile (E.212)</li>
     *     <li>8: National</li>
     *     <li>9: Private</li>
     *     <li>10: ERMES</li>
     *     <li>13: Internet (IP)</li>
     *     <li>18: WAP Client Id (to be defined by WAP Forum)</li>
     * </ul>
     */
    public void setNumberingPlanIndicator(byte numberingPlanIndicator) {
        this.numberingPlanIndicator = numberingPlanIndicator;
    }

    public boolean isUsingSSL() {
        return usingSSL;
    }

    /**
     * Whether using SSL with the smpps protocol
     */
    public void setUsingSSL(boolean usingSSL) {
        this.usingSSL = usingSSL;
    }

    public long getInitialReconnectDelay() {
        return initialReconnectDelay;
    }

    /**
     * Defines the initial delay in milliseconds after the consumer/producer tries to reconnect to the SMSC, after the connection was lost.
     */
    public void setInitialReconnectDelay(long initialReconnectDelay) {
        this.initialReconnectDelay = initialReconnectDelay;
    }

    public long getReconnectDelay() {
        return reconnectDelay;
    }

    /**
     * Defines the interval in milliseconds between the reconnect attempts, if the connection to the SMSC was lost and the previous was not succeed.
     */
    public void setReconnectDelay(long reconnectDelay) {
        this.reconnectDelay = reconnectDelay;
    }

    /**
     * Defines the maximum number of attempts to reconnect to the SMSC, if SMSC returns a negative bind response
     */
    public int getMaxReconnect() {
        return maxReconnect;
    }

    public void setMaxReconnect(int maxReconnect) {
        this.maxReconnect = maxReconnect;
    }

    public boolean isLazySessionCreation() {
        return lazySessionCreation;
    }

    /**
     * Sessions can be lazily created to avoid exceptions, if the SMSC is not available when the Camel producer is started.
     * Camel will check the in message headers 'CamelSmppSystemId' and 'CamelSmppPassword' of the first exchange.
     * If they are present, Camel will use these data to connect to the SMSC.
     */
    public void setLazySessionCreation(boolean lazySessionCreation) {
        this.lazySessionCreation = lazySessionCreation;
    }

    public String getHttpProxyHost() {
        return httpProxyHost;
    }

    /**
     * If you need to tunnel SMPP through a HTTP proxy, set this attribute to the hostname or ip address of your HTTP proxy.
     */
    public void setHttpProxyHost(String httpProxyHost) {
        this.httpProxyHost = httpProxyHost;
    }

    public Integer getHttpProxyPort() {
        return httpProxyPort;
    }

    /**
     * If you need to tunnel SMPP through a HTTP proxy, set this attribute to the port of your HTTP proxy.
     */
    public void setHttpProxyPort(Integer httpProxyPort) {
        this.httpProxyPort = httpProxyPort;
    }

    public String getHttpProxyUsername() {
        return httpProxyUsername;
    }

    /**
     * If your HTTP proxy requires basic authentication, set this attribute to the username required for your HTTP proxy.
     */
    public void setHttpProxyUsername(String httpProxyUsername) {
        this.httpProxyUsername = httpProxyUsername;
    }

    public String getHttpProxyPassword() {
        return httpProxyPassword;
    }

    /**
     * If your HTTP proxy requires basic authentication, set this attribute to the password required for your HTTP proxy.
     */
    public void setHttpProxyPassword(String httpProxyPassword) {
        this.httpProxyPassword = httpProxyPassword;
    }

    public SessionStateListener getSessionStateListener() {
        return sessionStateListener;
    }

    /**
     * You can refer to a org.jsmpp.session.SessionStateListener in the Registry to receive callbacks when the session state changed.
     */
    public void setSessionStateListener(SessionStateListener sessionStateListener) {
        this.sessionStateListener = sessionStateListener;
    }

    public String getAddressRange() {
        return addressRange;
    }

    /**
     *  You can specify the address range for the SmppConsumer as defined in section 5.2.7 of the SMPP 3.4 specification.
     *  The SmppConsumer will receive messages only from SMSC's which target an address (MSISDN or IP address) within this range.
     */
    public void setAddressRange(String addressRange) {
        this.addressRange = addressRange;
    }

    public SmppSplittingPolicy getSplittingPolicy() {
        return splittingPolicy;
    }

    /**
     * You can specify a policy for handling long messages:
     * <ul>
     *     <li>ALLOW - the default, long messages are split to 140 bytes per message</li>
     *     <li>TRUNCATE - long messages are split and only the first fragment will be sent to the SMSC.
     *     Some carriers drop subsequent fragments so this reduces load on the SMPP connection sending parts of a message that will never be delivered.</li>
     *     <li>REJECT - if a message would need to be split, it is rejected with an SMPP NegativeResponseException and the reason code signifying the message is too long.</li>
     * </ul>
     */
    public void setSplittingPolicy(SmppSplittingPolicy splittingPolicy) {
        this.splittingPolicy = splittingPolicy;
    }

    /**
     * These headers will be passed to the proxy server while establishing the connection.
     */
    public void setProxyHeaders(Map<String, String> proxyHeaders) {
        this.proxyHeaders = proxyHeaders;
    }

    public Map<String, String> getProxyHeaders() {
        return proxyHeaders;
    }

    @Override
    public String toString() {
        return "SmppConfiguration[usingSSL=" + usingSSL
            + ", enquireLinkTimer=" + enquireLinkTimer
            + ", host=" + host
            + ", password=" + password
            + ", port=" + port
            + ", systemId=" + systemId
            + ", systemType=" + systemType
            + ", dataCoding=" + dataCoding
            + ", alphabet=" + alphabet
            + ", encoding=" + encoding
            + ", transactionTimer=" + transactionTimer
            + ", registeredDelivery=" + registeredDelivery
            + ", serviceType=" + serviceType
            + ", sourceAddrTon=" + sourceAddrTon
            + ", destAddrTon=" + destAddrTon
            + ", sourceAddrNpi=" + sourceAddrNpi
            + ", destAddrNpi=" + destAddrNpi
            + ", addressRange=" + addressRange
            + ", protocolId=" + protocolId
            + ", priorityFlag=" + priorityFlag
            + ", replaceIfPresentFlag=" + replaceIfPresentFlag
            + ", sourceAddr=" + sourceAddr
            + ", destAddr=" + destAddr
            + ", typeOfNumber=" + typeOfNumber
            + ", numberingPlanIndicator=" + numberingPlanIndicator
            + ", initialReconnectDelay=" + initialReconnectDelay
            + ", reconnectDelay=" + reconnectDelay
            + ", maxReconnect=" + maxReconnect
            + ", lazySessionCreation=" + lazySessionCreation
            + ", httpProxyHost=" + httpProxyHost
            + ", httpProxyPort=" + httpProxyPort
            + ", httpProxyUsername=" + httpProxyUsername
            + ", httpProxyPassword=" + httpProxyPassword
            + ", splittingPolicy=" + splittingPolicy
            + ", proxyHeaders=" + proxyHeaders
            + "]";
    }
}
