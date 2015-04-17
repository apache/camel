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
package org.apache.camel.component.smpp;

import java.net.URI;
import java.nio.charset.Charset;

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
 * 
 * @version 
 */
@UriParams
public class SmppConfiguration implements Cloneable {
    private static final Logger LOG = LoggerFactory.getLogger(SmppConfiguration.class);

    @UriPath(defaultValue = "localhost")
    private String host = "localhost";
    @UriPath(defaultValue = "2775")
    private Integer port = 2775;
    @UriParam(defaultValue = "smppclient")
    private String systemId = "smppclient";
    @UriParam
    private String password = "password";
    @UriParam(defaultValue = "cp")
    private String systemType = "cp";
    @UriParam
    private byte dataCoding = (byte) 0;
    @UriParam
    private byte alphabet = Alphabet.ALPHA_DEFAULT.value();
    @UriParam(defaultValue = "ISO-8859-1")
    private String encoding = "ISO-8859-1";
    @UriParam(defaultValue = "5000")
    private Integer enquireLinkTimer = 5000;
    @UriParam(defaultValue = "10000")
    private Integer transactionTimer = 10000;
    @UriParam
    private byte registeredDelivery = SMSCDeliveryReceipt.SUCCESS_FAILURE.value();
    @UriParam(defaultValue = "CMT")
    private String serviceType = "CMT";
    @UriParam(defaultValue = "1616")
    private String sourceAddr = "1616";
    @UriParam(defaultValue = "1717")
    private String destAddr = "1717";
    @UriParam
    private byte sourceAddrTon = TypeOfNumber.UNKNOWN.value();
    @UriParam
    private byte destAddrTon = TypeOfNumber.UNKNOWN.value();
    @UriParam
    private byte sourceAddrNpi = NumberingPlanIndicator.UNKNOWN.value();
    @UriParam
    private byte destAddrNpi = NumberingPlanIndicator.UNKNOWN.value();
    @UriParam
    private String addressRange = "";
    @UriParam
    private byte protocolId = (byte) 0;
    @UriParam
    private byte priorityFlag = (byte) 1;
    @UriParam
    private byte replaceIfPresentFlag = ReplaceIfPresentFlag.DEFAULT.value();
    @UriParam
    private byte typeOfNumber = TypeOfNumber.UNKNOWN.value();
    @UriParam
    private byte numberingPlanIndicator = NumberingPlanIndicator.UNKNOWN.value();
    @UriParam
    private boolean usingSSL;
    @UriParam(defaultValue = "5000")
    private long initialReconnectDelay = 5000;
    @UriParam(defaultValue = "5000")
    private long reconnectDelay = 5000;
    @UriParam
    private boolean lazySessionCreation;
    @UriParam
    private String httpProxyHost;
    @UriParam(defaultValue = "3128")
    private Integer httpProxyPort = 3128;
    @UriParam
    private String httpProxyUsername;
    @UriParam
    private String httpProxyPassword;
    @UriParam
    private SessionStateListener sessionStateListener;
    @UriParam(defaultValue = "ALLOW")
    private SmppSplittingPolicy splittingPolicy = SmppSplittingPolicy.ALLOW;

    
    /**
     * A POJO which contains all necessary configuration parameters for the SMPP connection
     * 
     * @param uri the full URI of the endpoint
     */
    public void configureFromURI(URI uri) {
        setSystemId(uri.getUserInfo());
        setHost(uri.getHost());
        setPort(uri.getPort());
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

    public boolean getUsingSSL() {
        return usingSSL;
    }
    
    public void setUsingSSL(boolean usingSSL) {
        this.usingSSL = usingSSL;
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
    
    public boolean isLazySessionCreation() {
        return lazySessionCreation;
    }

    public void setLazySessionCreation(boolean lazySessionCreation) {
        this.lazySessionCreation = lazySessionCreation;
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
            + ", lazySessionCreation=" + lazySessionCreation
            + ", httpProxyHost=" + httpProxyHost
            + ", httpProxyPort=" + httpProxyPort
            + ", httpProxyUsername=" + httpProxyUsername
            + ", httpProxyPassword=" + httpProxyPassword
            + ", splittingPolicy=" + splittingPolicy
            + "]";
    }
}
