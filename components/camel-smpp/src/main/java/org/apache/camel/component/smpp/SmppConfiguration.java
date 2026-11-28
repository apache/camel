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
import org.jsmpp.bean.*;
import org.jsmpp.session.SessionStateListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Contains the SMPP component configuration properties</a>
 */
@UriParams
public class SmppConfiguration implements Cloneable {
    private static final Logger LOG = LoggerFactory.getLogger(SmppConfiguration.class);

    @UriPath(description = "The host name or IP address of the SMSC.",
             defaultValue = "localhost")
    private String host = "localhost";
    @UriPath(description = "The TCP port of the SMSC.",
             defaultValue = "2775")
    private Integer port = 2775;
    @UriParam(description = "The system_id with which the consumer or producer binds to the SMSC: the user name that the "
                            + "SMSC authenticates. The user info of the URI, as in smpp://user@host, sets it too.",
              label = "security", defaultValue = "smppclient", security = "secret")
    private String systemId = "smppclient";
    @UriParam(description = "The password with which the consumer or producer binds to the SMSC.",
              label = "security", security = "secret")
    private String password;
    @UriParam(description = "The system_type of the bind request, which tells the SMSC the type of ESME (External Short "
                            + "Message Entity) that binds. SMPP 3.4 allows at most 13 octets, the NULL terminator included. "
                            + "Empty by default.",
              label = "common", defaultValue = "")
    private String systemType = "";
    @UriParam(description = "The data_coding that the producer sends with SubmitSm, SubmitMulti and DataSm, as defined in "
                            + "section 5.2.19 of the SMPP 3.4 specification, for example 0 for the SMSC default alphabet, 3 "
                            + "for Latin 1, 4 for 8-bit binary data and 8 for UCS2. An 8-bit value sends a byte array body as "
                            + "it is; the alphabet and encoding options, not this option, select how a text body is encoded. "
                            + "The CamelSmppDataCoding and CamelSmppAlphabet headers replace it; the default is 0.",
              label = "codec")
    private byte dataCoding = (byte) 0;
    @UriParam(description = "The alphabet in which the producer encodes and splits the text of the short message for "
                            + "SubmitSm, SubmitMulti and ReplaceSm: 0 (the SMSC default alphabet) encodes it in the charset "
                            + "of the encoding option, 8 (UCS2) in UTF-16BE, and 4 (8-bit) sends a byte array body as it is. "
                            + "It does not set the data_coding that is sent, which comes from the dataCoding option. The "
                            + "CamelSmppAlphabet header replaces it; the default is 0.",
              label = "codec", enums = "0,4,8")
    private byte alphabet = Alphabet.ALPHA_DEFAULT.value();
    @UriParam(description = "The charset in which the producer encodes the text of the short message for SubmitSm, "
                            + "SubmitMulti and ReplaceSm, unless the alphabet is UCS2, and in which the consumer decodes a "
                            + "received message in the SMSC default alphabet. The CamelSmppEncoding header replaces it for the "
                            + "producer.",
              label = "codec", defaultValue = "ISO-8859-1")
    private String encoding = "ISO-8859-1";
    @UriParam(description = "The time in milliseconds without data from the SMSC after which the session sends an "
                            + "enquire_link to check the connection, or 0 to send none. Camel also uses it as the timeout to "
                            + "connect to the SMSC or the HTTP proxy, and for the TLS handshake.",
              label = "advanced", defaultValue = "60000")
    private Integer enquireLinkTimer = 60000;
    @UriParam(description = "The time in milliseconds that the session waits for the response of the SMSC to a request that "
                            + "it sent, such as a submit_sm or an enquire_link, before the request fails. The bind request "
                            + "waits 60 seconds instead.",
              label = "advanced", defaultValue = "10000")
    private Integer transactionTimer = 10000;
    @UriParam(description = "The registered_delivery that the producer sends with SubmitSm, SubmitMulti, ReplaceSm and "
                            + "DataSm: 0 requests no delivery receipt, 1 a delivery receipt for success and for failure, and 2 "
                            + "a delivery receipt for failure only. The CamelSmppRegisteredDelivery header replaces it; the "
                            + "default is 1.",
              label = "producer", enums = "0,1,2")
    private byte registeredDelivery = SMSCDeliveryReceipt.SUCCESS_FAILURE.value();
    @UriParam(description = "The service_type that the producer sends with SubmitSm, SubmitMulti, CancelSm and DataSm: the "
                            + "SMS application service of the message, such as CMT (cellular messaging), CPT (cellular "
                            + "paging), VMN (voice mail notification), VMA (voice mail alerting), WAP or USSD. Empty by "
                            + "default, for the default service of the SMSC. The CamelSmppServiceType header replaces it.",
              label = "producer", defaultValue = "", enums = "CMT,CPT,VMN,VMA,WAP,USSD")
    private String serviceType = "";
    @UriParam(description = "The source address that the producer sends with every command: the address of the SME (Short "
                            + "Message Entity) that sends the message, or, for ReplaceSm, QuerySm and CancelSm, the source "
                            + "address of the original message. The CamelSmppSourceAddr header replaces it.",
              label = "producer", defaultValue = "1616")
    private String sourceAddr = "1616";
    @UriParam(description = "The destination address that the producer sends with SubmitSm, SubmitMulti, CancelSm and "
                            + "DataSm, for example the phone number of the recipient of a mobile terminated message. The "
                            + "CamelSmppDestAddr header replaces it, with a list of addresses for SubmitMulti.",
              label = "producer", defaultValue = "1717")
    private String destAddr = "1717";
    @UriParam(description = "The type of number (TON) of the source address that the producer sends with every command: 0 "
                            + "unknown, 1 international, 2 national, 3 network specific, 4 subscriber number, 5 alphanumeric "
                            + "or 6 abbreviated. The CamelSmppSourceAddrTon header replaces it; the default is 0.",
              label = "producer", enums = "0,1,2,3,4,5,6")
    private byte sourceAddrTon = TypeOfNumber.UNKNOWN.value();
    @UriParam(description = "The type of number (TON) of the destination address that the producer sends with SubmitSm, "
                            + "SubmitMulti, CancelSm and DataSm, with the values of the sourceAddrTon option. The "
                            + "CamelSmppDestAddrTon header replaces it; the default is 0.",
              label = "producer", enums = "0,1,2,3,4,5,6")
    private byte destAddrTon = TypeOfNumber.UNKNOWN.value();
    @UriParam(description = "The numbering plan indicator (NPI) of the source address that the producer sends with every "
                            + "command: 0 unknown, 1 ISDN (E.163/E.164), 2 data (X.121), 3 telex (F.69), 6 land mobile "
                            + "(E.212), 8 national, 9 private, 10 ERMES, 13 Internet (IP) or 18 WAP client id. The "
                            + "CamelSmppSourceAddrNpi header replaces it; the default is 0.",
              label = "producer", enums = "0,1,2,3,6,8,9,10,13,18")
    private byte sourceAddrNpi = NumberingPlanIndicator.UNKNOWN.value();
    @UriParam(description = "The numbering plan indicator (NPI) of the destination address that the producer sends with "
                            + "SubmitSm, SubmitMulti, CancelSm and DataSm, with the values of the sourceAddrNpi option. The "
                            + "CamelSmppDestAddrNpi header replaces it; the default is 0.",
              label = "producer", enums = "0,1,2,3,6,8,9,10,13,18")
    private byte destAddrNpi = NumberingPlanIndicator.UNKNOWN.value();
    @UriParam(description = "The address_range of the bind request of the consumer: the SME addresses that this ESME serves, "
                            + "as defined in section 5.2.7 of the SMPP 3.4 specification. The SMSC uses it to select the "
                            + "messages that it delivers to the consumer. Empty by default.",
              label = "consumer")
    private String addressRange = "";
    @UriParam(description = "The protocol_id that the producer sends with SubmitSm and SubmitMulti, which for GSM is set as "
                            + "defined in GSM 03.40. The CamelSmppProtocolId header replaces it; the default is 0.",
              label = "producer")
    private byte protocolId = (byte) 0;
    @UriParam(description = "The priority_flag that the producer sends with SubmitSm and SubmitMulti, from 0 (lowest) to 3 "
                            + "(highest). The CamelSmppPriorityFlag header replaces it; the default is 1.",
              label = "producer", enums = "0,1,2,3")
    private byte priorityFlag = (byte) 1;
    @UriParam(description = "The replace_if_present_flag that the producer sends with SubmitSm and SubmitMulti: 1 asks the "
                            + "SMSC to replace a message that is still pending delivery and has the same source address, "
                            + "destination address and service type, 0 does not. The CamelSmppReplaceIfPresentFlag header "
                            + "replaces it; the default is 0.",
              label = "producer", enums = "0,1")
    private byte replaceIfPresentFlag = ReplaceIfPresentFlag.DEFAULT.value();
    @UriParam(description = "The type of number (TON) that the producer sends in the addr_ton field of its bind request, "
                            + "with the values of the sourceAddrTon option. The consumer binds with 0 (unknown). The default "
                            + "is 0.",
              label = "producer", enums = "0,1,2,3,4,5,6")
    private byte typeOfNumber = TypeOfNumber.UNKNOWN.value();
    @UriParam(description = "The numbering plan indicator (NPI) that the producer sends in the addr_npi field of its bind "
                            + "request, with the values of the sourceAddrNpi option. The consumer binds with 0 (unknown). The "
                            + "default is 0.",
              label = "producer", enums = "0,1,2,3,6,8,9,10,13,18")
    private byte numberingPlanIndicator = NumberingPlanIndicator.UNKNOWN.value();
    @UriParam(description = "Whether to connect to the SMSC with TLS, which the smpps scheme enables. The certificate of the "
                            + "SMSC must be trusted by the default SSL socket factory of the JVM. Through an HTTP proxy, TLS "
                            + "runs inside the tunnel.",
              label = "security")
    private boolean usingSSL;
    @UriParam(label = "security", defaultValue = "true", security = "insecure:ssl", insecureValue = "false")
    private boolean hostnameVerification = true;
    @UriParam(description = "The delay in milliseconds before the first attempt to reconnect, after the consumer or producer "
                            + "lost its session to the SMSC (the connection was closed, or the SMSC unbound). A failed connect "
                            + "when the consumer or producer starts is not retried.",
              label = "common", defaultValue = "5000")
    private long initialReconnectDelay = 5000;
    @UriParam(description = "The delay in milliseconds between two attempts to reconnect to the SMSC, after the first "
                            + "attempt failed.",
              label = "common", defaultValue = "5000")
    private long reconnectDelay = 5000;
    @UriParam(description = "The maximum number of attempts to reconnect after the session to the SMSC was lost. When the "
                            + "last attempt fails, a consumer stays disconnected until it is restarted, and a producer fails "
                            + "its exchanges, unless lazySessionCreation is enabled, which connects again for the next "
                            + "exchange.",
              label = "common", defaultValue = "2147483647")
    private int maxReconnect = Integer.MAX_VALUE;
    @UriParam(description = "Whether the producer connects to the SMSC when it sends its first exchange instead of when it "
                            + "starts, so that the route can start while the SMSC is not available. The producer binds with "
                            + "the systemId and password options, or with headers when allowSystemIdAndPasswordFromHeaders is "
                            + "enabled.",
              label = "producer")
    private boolean lazySessionCreation;
    @UriParam(label = "producer,security")
    private boolean allowSystemIdAndPasswordFromHeaders;
    @UriParam(description = "The id of a route whose consumer receives the messages that the SMSC sends to this producer. "
                            + "The producer then binds as a transceiver (bind_transceiver) instead of a transmitter, and hands "
                            + "each received deliver_sm, data_sm and alert_notification to that route, so that no SMPP "
                            + "consumer is needed. Only tested with a route that starts from a direct endpoint; the route must "
                            + "exist when Camel starts.",
              label = "producer")
    private String messageReceiverRouteId;
    @UriParam(description = "The host name or IP address of an HTTP proxy through which Camel tunnels the SMPP connection, "
                            + "with an HTTP CONNECT request. When not set, Camel connects to the SMSC directly.",
              label = "proxy")
    private String httpProxyHost;
    @UriParam(description = "The port of the HTTP proxy that the httpProxyHost option sets.",
              label = "proxy", defaultValue = "3128")
    private Integer httpProxyPort = 3128;
    @UriParam(description = "The user name for Basic authentication with the HTTP proxy. It is sent only together with the "
                            + "httpProxyPassword option.",
              label = "proxy")
    private String httpProxyUsername;
    @UriParam(description = "The password for Basic authentication with the HTTP proxy. It is sent only together with the "
                            + "httpProxyUsername option.",
              label = "proxy", security = "secret")
    private String httpProxyPassword;
    @UriParam(description = "Extra HTTP headers that Camel sends to the HTTP proxy with the CONNECT request.",
              label = "proxy")
    private Map<String, String> proxyHeaders;
    @UriParam(description = "An org.jsmpp.session.SessionStateListener that is notified when the state of the SMPP session "
                            + "of the consumer or producer changes, for example from BOUND_RX to CLOSED. Camel still "
                            + "reconnects a lost session itself.",
              label = "advanced")
    private SessionStateListener sessionStateListener;
    @UriParam(description = "What the producer does with a SubmitSm or SubmitMulti whose body does not fit in one short "
                            + "message: ALLOW sends it in several segments, TRUNCATE sends only its first part, and REJECT "
                            + "fails the exchange with an SmppException caused by a NegativeResponseException with status 1. "
                            + "Some carriers drop the segments after the first, which TRUNCATE does not send. The "
                            + "CamelSmppSplittingPolicy header replaces it.",
              defaultValue = "ALLOW")
    private SmppSplittingPolicy splittingPolicy = SmppSplittingPolicy.ALLOW;
    @UriParam(description = "The number of threads of the SMPP session that process the received PDUs in parallel.",
              label = "advanced", defaultValue = "3")
    private Integer pduProcessorDegree = 3;
    @UriParam(description = "The number of received PDUs that can wait for a free processing thread. When the queue is full, "
                            + "the session answers a received request with the ESME_RTHROTTLED error, so that the SMSC slows "
                            + "down.",
              label = "advanced", defaultValue = "100")
    private Integer pduProcessorQueueCapacity = 100;
    @UriParam(label = "advanced", defaultValue = "false")
    private boolean singleDLR;
    @UriParam(label = "advanced", enums = "legacy,3.3,3.4,5.0", defaultValue = "3.4")
    private String interfaceVersion = "3.4";

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

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getSystemId() {
        return systemId;
    }

    public void setSystemId(String systemId) {
        this.systemId = systemId;
    }

    public String getPassword() {
        return password;
    }

    public byte getDataCoding() {
        return dataCoding;
    }

    public void setDataCoding(byte dataCoding) {
        this.dataCoding = dataCoding;
    }

    public byte getAlphabet() {
        return alphabet;
    }

    public void setAlphabet(byte alphabet) {
        this.alphabet = alphabet;
    }

    public String getEncoding() {
        return encoding;
    }

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

    public void setEnquireLinkTimer(Integer enquireLinkTimer) {
        this.enquireLinkTimer = enquireLinkTimer;
    }

    public Integer getTransactionTimer() {
        return transactionTimer;
    }

    public void setTransactionTimer(Integer transactionTimer) {
        this.transactionTimer = transactionTimer;
    }

    public String getSystemType() {
        return systemType;
    }

    public void setSystemType(String systemType) {
        this.systemType = systemType;
    }

    public byte getRegisteredDelivery() {
        return registeredDelivery;
    }

    public void setRegisteredDelivery(byte registeredDelivery) {
        this.registeredDelivery = registeredDelivery;
    }

    public String getServiceType() {
        return serviceType;
    }

    public void setServiceType(String serviceType) {
        this.serviceType = serviceType;
    }

    public byte getSourceAddrTon() {
        return sourceAddrTon;
    }

    public void setSourceAddrTon(byte sourceAddrTon) {
        this.sourceAddrTon = sourceAddrTon;
    }

    public byte getDestAddrTon() {
        return destAddrTon;
    }

    public void setDestAddrTon(byte destAddrTon) {
        this.destAddrTon = destAddrTon;
    }

    public byte getSourceAddrNpi() {
        return sourceAddrNpi;
    }

    public void setSourceAddrNpi(byte sourceAddrNpi) {
        this.sourceAddrNpi = sourceAddrNpi;
    }

    public byte getDestAddrNpi() {
        return destAddrNpi;
    }

    public void setDestAddrNpi(byte destAddrNpi) {
        this.destAddrNpi = destAddrNpi;
    }

    public byte getProtocolId() {
        return protocolId;
    }

    public void setProtocolId(byte protocolId) {
        this.protocolId = protocolId;
    }

    public byte getPriorityFlag() {
        return priorityFlag;
    }

    public void setPriorityFlag(byte priorityFlag) {
        this.priorityFlag = priorityFlag;
    }

    public byte getReplaceIfPresentFlag() {
        return replaceIfPresentFlag;
    }

    public void setReplaceIfPresentFlag(byte replaceIfPresentFlag) {
        this.replaceIfPresentFlag = replaceIfPresentFlag;
    }

    public String getSourceAddr() {
        return sourceAddr;
    }

    public void setSourceAddr(String sourceAddr) {
        this.sourceAddr = sourceAddr;
    }

    public String getDestAddr() {
        return destAddr;
    }

    public void setDestAddr(String destAddr) {
        this.destAddr = destAddr;
    }

    public byte getTypeOfNumber() {
        return typeOfNumber;
    }

    public void setTypeOfNumber(byte typeOfNumber) {
        this.typeOfNumber = typeOfNumber;
    }

    public byte getNumberingPlanIndicator() {
        return numberingPlanIndicator;
    }

    public void setNumberingPlanIndicator(byte numberingPlanIndicator) {
        this.numberingPlanIndicator = numberingPlanIndicator;
    }

    public boolean isUsingSSL() {
        return usingSSL;
    }

    public void setUsingSSL(boolean usingSSL) {
        this.usingSSL = usingSSL;
    }

    public boolean isHostnameVerification() {
        return hostnameVerification;
    }

    /**
     * Whether to check that the certificate of the SMSC matches the host Camel connects to, when SSL is used. When
     * disabled, any certificate trusted by the JVM is accepted, whatever host it was issued for.
     */
    public void setHostnameVerification(boolean hostnameVerification) {
        this.hostnameVerification = hostnameVerification;
    }

    public long getInitialReconnectDelay() {
        return initialReconnectDelay;
    }

    public void setInitialReconnectDelay(long initialReconnectDelay) {
        this.initialReconnectDelay = initialReconnectDelay;
    }

    public long getReconnectDelay() {
        return reconnectDelay;
    }

    public void setReconnectDelay(long reconnectDelay) {
        this.reconnectDelay = reconnectDelay;
    }

    public int getMaxReconnect() {
        return maxReconnect;
    }

    public void setMaxReconnect(int maxReconnect) {
        this.maxReconnect = maxReconnect;
    }

    public boolean isLazySessionCreation() {
        return lazySessionCreation;
    }

    public void setLazySessionCreation(boolean lazySessionCreation) {
        this.lazySessionCreation = lazySessionCreation;
    }

    public boolean isAllowSystemIdAndPasswordFromHeaders() {
        return allowSystemIdAndPasswordFromHeaders;
    }

    /**
     * Whether a producer with lazySessionCreation=true may take the system id and the password, with which it connects
     * to the SMSC, from the 'CamelSmppSystemId' and 'CamelSmppPassword' headers of the first exchange. When both
     * headers are present, the producer connects with them in place of the systemId and password of the endpoint, and
     * keeps using them when it reconnects. This is disabled by default, and the headers are then ignored. Enable this
     * only when the route sets these headers itself.
     */
    public void setAllowSystemIdAndPasswordFromHeaders(boolean allowSystemIdAndPasswordFromHeaders) {
        this.allowSystemIdAndPasswordFromHeaders = allowSystemIdAndPasswordFromHeaders;
    }

    public String getMessageReceiverRouteId() {
        return messageReceiverRouteId;
    }

    public void setMessageReceiverRouteId(String messageReceiverRouteId) {
        this.messageReceiverRouteId = messageReceiverRouteId;
    }

    public String getHttpProxyHost() {
        return httpProxyHost;
    }

    public void setHttpProxyHost(String httpProxyHost) {
        this.httpProxyHost = httpProxyHost;
    }

    public Integer getHttpProxyPort() {
        return httpProxyPort;
    }

    public void setHttpProxyPort(Integer httpProxyPort) {
        this.httpProxyPort = httpProxyPort;
    }

    public String getHttpProxyUsername() {
        return httpProxyUsername;
    }

    public void setHttpProxyUsername(String httpProxyUsername) {
        this.httpProxyUsername = httpProxyUsername;
    }

    public String getHttpProxyPassword() {
        return httpProxyPassword;
    }

    public void setHttpProxyPassword(String httpProxyPassword) {
        this.httpProxyPassword = httpProxyPassword;
    }

    public SessionStateListener getSessionStateListener() {
        return sessionStateListener;
    }

    public void setSessionStateListener(SessionStateListener sessionStateListener) {
        this.sessionStateListener = sessionStateListener;
    }

    public String getAddressRange() {
        return addressRange;
    }

    public void setAddressRange(String addressRange) {
        this.addressRange = addressRange;
    }

    public SmppSplittingPolicy getSplittingPolicy() {
        return splittingPolicy;
    }

    public void setSplittingPolicy(SmppSplittingPolicy splittingPolicy) {
        this.splittingPolicy = splittingPolicy;
    }

    public void setProxyHeaders(Map<String, String> proxyHeaders) {
        this.proxyHeaders = proxyHeaders;
    }

    public Map<String, String> getProxyHeaders() {
        return proxyHeaders;
    }

    public Integer getPduProcessorDegree() {
        return pduProcessorDegree;
    }

    public void setPduProcessorDegree(Integer pduProcessorDegree) {
        this.pduProcessorDegree = pduProcessorDegree;
    }

    public Integer getPduProcessorQueueCapacity() {
        return pduProcessorQueueCapacity;
    }

    public void setPduProcessorQueueCapacity(Integer pduProcessorQueueCapacity) {
        this.pduProcessorQueueCapacity = pduProcessorQueueCapacity;
    }

    public boolean isSingleDLR() {
        return singleDLR;
    }

    /**
     * When true, the SMSC delivery receipt would be requested only for the last segment of a multi-segment (long)
     * message. For short messages, with only 1 segment the behaviour is unchanged.
     */
    public void setSingleDLR(boolean singleDLR) {
        this.singleDLR = singleDLR;
    }

    public String getInterfaceVersion() {
        return interfaceVersion;
    }

    /**
     * Defines the interface version to be used in the binding request with the SMSC. The following values are allowed,
     * as defined in the SMPP protocol (and the underlying implementation using the jSMPP library, respectively):
     * "legacy" (0x00), "3.3" (0x33), "3.4" (0x34), and "5.0" (0x50). The default (fallback) value is version 3.4.
     */
    public void setInterfaceVersion(String interfaceVersion) {
        this.interfaceVersion = interfaceVersion;
    }

    public InterfaceVersion getInterfaceVersionByte() {
        return switch (interfaceVersion) {
            case "legacy" -> InterfaceVersion.IF_00;
            case "3.3" -> InterfaceVersion.IF_33;
            case "3.4" -> InterfaceVersion.IF_34;
            case "5.0" -> InterfaceVersion.IF_50;
            default -> InterfaceVersion.IF_34;
        };
    }

    @Override
    public String toString() {
        return "SmppConfiguration[usingSSL=" + usingSSL
               + ", hostnameVerification=" + hostnameVerification
               + ", enquireLinkTimer=" + enquireLinkTimer
               + ", host=" + host
               + ", password=" + mask(password)
               + ", port=" + port
               + ", systemId=" + systemId
               + ", systemType=" + systemType
               + ", dataCoding=" + dataCoding
               + ", alphabet=" + alphabet
               + ", encoding=" + encoding
               + ", transactionTimer=" + transactionTimer
               + ", pduProcessorQueueCapacity=" + pduProcessorQueueCapacity
               + ", pduProcessorDegree=" + pduProcessorDegree
               + ", registeredDelivery=" + registeredDelivery
               + ", singleDLR=" + singleDLR
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
               + ", allowSystemIdAndPasswordFromHeaders=" + allowSystemIdAndPasswordFromHeaders
               + ", messageReceiverRouteId=" + messageReceiverRouteId
               + ", httpProxyHost=" + httpProxyHost
               + ", httpProxyPort=" + httpProxyPort
               + ", httpProxyUsername=" + httpProxyUsername
               + ", httpProxyPassword=" + mask(httpProxyPassword)
               + ", splittingPolicy=" + splittingPolicy
               + ", proxyHeaders=" + proxyHeaders
               + ", interfaceVersion=" + interfaceVersion
               + "]";
    }

    /**
     * Masks a credential so that logging or a JMX attribute of this configuration cannot leak it. {@code xxxxxx} is the
     * mask Camel uses when it sanitizes a URI, and whether the value was set at all is still visible.
     */
    private static String mask(Object secret) {
        return secret != null ? "xxxxxx" : "null";
    }
}
