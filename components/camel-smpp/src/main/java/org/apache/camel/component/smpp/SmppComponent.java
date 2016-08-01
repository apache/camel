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
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.UriEndpointComponent;
import org.jsmpp.session.SessionStateListener;

/**
 * @version 
 */
public class SmppComponent extends UriEndpointComponent {

    private SmppConfiguration configuration;

    public SmppComponent() {
        super(SmppEndpoint.class);
    }

    public SmppComponent(SmppConfiguration configuration) {
        this();
        this.configuration = configuration;
    }

    public SmppComponent(CamelContext context) {
        super(context, SmppEndpoint.class);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map parameters) throws Exception {
        if (this.configuration == null) {
            this.configuration = new SmppConfiguration();
        }

        // create a copy of the configuration as other endpoints can adjust their copy as well
        SmppConfiguration config = this.configuration.copy();

        config.configureFromURI(new URI(uri));
        // TODO Camel 3.0 cmueller: We should change the default in Camel 3.0 to '' so that we can remove this special handling
        // special handling to set the system type to an empty string
        if (parameters.containsKey("systemType") && parameters.get("systemType") == null) {
            config.setSystemType("");
            parameters.remove("systemType");
        }
        // special handling to set the service type to an empty string
        if (parameters.containsKey("serviceType") && parameters.get("serviceType") == null) {
            config.setServiceType("");
            parameters.remove("serviceType");
        }
        setProperties(config, parameters);

        return createEndpoint(uri, config);
    }

    /**
     * Create a new smpp endpoint with the provided smpp configuration
     */
    protected Endpoint createEndpoint(SmppConfiguration config) throws Exception {
        return createEndpoint(null, config);
    }

    /**
     * Create a new smpp endpoint with the provided uri and smpp configuration
     */
    protected Endpoint createEndpoint(String uri, SmppConfiguration config) throws Exception {
        return new SmppEndpoint(uri, this, config);
    }

    public SmppConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * To use the shared SmppConfiguration as configuration. Properties of the shared configuration can also be set individually.
     */
    public void setConfiguration(SmppConfiguration configuration) {
        this.configuration = configuration;
    }


    private SmppConfiguration getConfigurationOrCreate() {
        if (this.getConfiguration() == null) {
            this.setConfiguration(new SmppConfiguration());
        }
        return this.getConfiguration();
    }

    public String getHost() {
        return getConfigurationOrCreate().getHost();
    }

    /**
     * Hostname for the SMSC server to use.
     * @param host
     */
    public void setHost(String host) {
        getConfigurationOrCreate().setHost(host);
    }

    public Integer getPort() {
        return getConfigurationOrCreate().getPort();
    }

    /**
     * Port number for the SMSC server to use.
     * @param port
     */
    public void setPort(Integer port) {
        getConfigurationOrCreate().setPort(port);
    }

    public String getSystemId() {
        return getConfigurationOrCreate().getSystemId();
    }

    /**
     * The system id (username) for connecting to SMSC server.
     * @param systemId
     */
    public void setSystemId(String systemId) {
        getConfigurationOrCreate().setSystemId(systemId);
    }

    /**
     * The password for connecting to SMSC server.
     */
    public String getPassword() {
        return getConfigurationOrCreate().getPassword();
    }

    public byte getDataCoding() {
        return getConfigurationOrCreate().getDataCoding();
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
     * @param dataCoding
     */
    public void setDataCoding(byte dataCoding) {
        getConfigurationOrCreate().setDataCoding(dataCoding);
    }

    public byte getAlphabet() {
        return getConfigurationOrCreate().getAlphabet();
    }

    /**
     * Defines encoding of data according the SMPP 3.4 specification, section 5.2.19.
     * <ul>
     *     <li>0: SMSC Default Alphabet
     *     <li>4: 8 bit Alphabet</li>
     *     <li>8: UCS2 Alphabet</li></li>
     * </ul>
     * @param alphabet
     */
    public void setAlphabet(byte alphabet) {
        getConfigurationOrCreate().setAlphabet(alphabet);
    }

    public String getEncoding() {
        return getConfigurationOrCreate().getEncoding();
    }

    /**
     * Defines the encoding scheme of the short message user data.
     * Only for SubmitSm, ReplaceSm and SubmitMulti.
     * @param encoding
     */
    public void setEncoding(String encoding) {
        getConfigurationOrCreate().setEncoding(encoding);
    }

    public void setPassword(String password) {
        getConfigurationOrCreate().setPassword(password);
    }

    public Integer getEnquireLinkTimer() {
        return getConfigurationOrCreate().getEnquireLinkTimer();
    }

    /**
     * Defines the interval in milliseconds between the confidence checks.
     * The confidence check is used to test the communication path between an ESME and an SMSC.
     * @param enquireLinkTimer
     */
    public void setEnquireLinkTimer(Integer enquireLinkTimer) {
        getConfigurationOrCreate().setEnquireLinkTimer(enquireLinkTimer);
    }

    public Integer getTransactionTimer() {
        return getConfigurationOrCreate().getTransactionTimer();
    }

    /**
     * Defines the maximum period of inactivity allowed after a transaction, after which
     * an SMPP entity may assume that the session is no longer active.
     * This timer may be active on either communicating SMPP entity (i.e. SMSC or ESME).
     * @param transactionTimer
     */
    public void setTransactionTimer(Integer transactionTimer) {
        getConfigurationOrCreate().setTransactionTimer(transactionTimer);
    }

    public String getSystemType() {
        return getConfigurationOrCreate().getSystemType();
    }

    /**
     * This parameter is used to categorize the type of ESME (External Short Message Entity) that is binding to the SMSC (max. 13 characters).
     * @param systemType
     */
    public void setSystemType(String systemType) {
        getConfigurationOrCreate().setSystemType(systemType);
    }

    public byte getRegisteredDelivery() {
        return getConfigurationOrCreate().getRegisteredDelivery();
    }

    /**
     * Is used to request an SMSC delivery receipt and/or SME originated acknowledgements. The following values are defined:
     * <ul>
     *     <li>0: No SMSC delivery receipt requested.</li>
     *     <li>1: SMSC delivery receipt requested where final delivery outcome is success or failure.</li>
     *     <li>2: SMSC delivery receipt requested where the final delivery outcome is delivery failure.</li>
     * </ul>
     * @param registeredDelivery
     */
    public void setRegisteredDelivery(byte registeredDelivery) {
        getConfigurationOrCreate().setRegisteredDelivery(registeredDelivery);
    }

    public String getServiceType() {
        return getConfigurationOrCreate().getServiceType();
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
     * @param serviceType
     */
    public void setServiceType(String serviceType) {
        getConfigurationOrCreate().setServiceType(serviceType);
    }

    public byte getSourceAddrTon() {
        return getConfigurationOrCreate().getSourceAddrTon();
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
     * @param sourceAddrTon
     */
    public void setSourceAddrTon(byte sourceAddrTon) {
        getConfigurationOrCreate().setSourceAddrTon(sourceAddrTon);
    }

    public byte getDestAddrTon() {
        return getConfigurationOrCreate().getDestAddrTon();
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
     * @param destAddrTon
     */
    public void setDestAddrTon(byte destAddrTon) {
        getConfigurationOrCreate().setDestAddrTon(destAddrTon);
    }

    public byte getSourceAddrNpi() {
        return getConfigurationOrCreate().getSourceAddrNpi();
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
     * @param sourceAddrNpi
     */
    public void setSourceAddrNpi(byte sourceAddrNpi) {
        getConfigurationOrCreate().setSourceAddrNpi(sourceAddrNpi);
    }

    public byte getDestAddrNpi() {
        return getConfigurationOrCreate().getDestAddrNpi();
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
     * @param destAddrNpi
     */
    public void setDestAddrNpi(byte destAddrNpi) {
        getConfigurationOrCreate().setDestAddrNpi(destAddrNpi);
    }

    public byte getProtocolId() {
        return getConfigurationOrCreate().getProtocolId();
    }

    /**
     * The protocol id
     * @param protocolId
     */
    public void setProtocolId(byte protocolId) {
        getConfigurationOrCreate().setProtocolId(protocolId);
    }

    public byte getPriorityFlag() {
        return getConfigurationOrCreate().getPriorityFlag();
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
     * @param priorityFlag
     */
    public void setPriorityFlag(byte priorityFlag) {
        getConfigurationOrCreate().setPriorityFlag(priorityFlag);
    }

    public byte getReplaceIfPresentFlag() {
        return getConfigurationOrCreate().getReplaceIfPresentFlag();
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
     * @param replaceIfPresentFlag
     */
    public void setReplaceIfPresentFlag(byte replaceIfPresentFlag) {
        getConfigurationOrCreate().setReplaceIfPresentFlag(replaceIfPresentFlag);
    }

    public String getSourceAddr() {
        return getConfigurationOrCreate().getSourceAddr();
    }

    /**
     * Defines the address of SME (Short Message Entity) which originated this message.
     * @param sourceAddr
     */
    public void setSourceAddr(String sourceAddr) {
        getConfigurationOrCreate().setSourceAddr(sourceAddr);
    }

    public String getDestAddr() {
        return getConfigurationOrCreate().getDestAddr();
    }

    /**
     * Defines the destination SME address. For mobile terminated messages, this is the directory number of the recipient MS.
     * Only for SubmitSm, SubmitMulti, CancelSm and DataSm.
     * @param destAddr
     */
    public void setDestAddr(String destAddr) {
        getConfigurationOrCreate().setDestAddr(destAddr);
    }

    public byte getTypeOfNumber() {
        return getConfigurationOrCreate().getTypeOfNumber();
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
     * @param typeOfNumber
     */
    public void setTypeOfNumber(byte typeOfNumber) {
        getConfigurationOrCreate().setTypeOfNumber(typeOfNumber);
    }

    public byte getNumberingPlanIndicator() {
        return getConfigurationOrCreate().getNumberingPlanIndicator();
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
     * @param numberingPlanIndicator
     */
    public void setNumberingPlanIndicator(byte numberingPlanIndicator) {
        getConfigurationOrCreate().setNumberingPlanIndicator(numberingPlanIndicator);
    }

    public boolean getUsingSSL() {
        return getConfigurationOrCreate().getUsingSSL();
    }

    /**
     * Whether using SSL with the smpps protocol
     * @param usingSSL
     */
    public void setUsingSSL(boolean usingSSL) {
        getConfigurationOrCreate().setUsingSSL(usingSSL);
    }

    public long getInitialReconnectDelay() {
        return getConfigurationOrCreate().getInitialReconnectDelay();
    }

    /**
     * Defines the initial delay in milliseconds after the consumer/producer tries to reconnect to the SMSC, after the connection was lost.
     * @param initialReconnectDelay
     */
    public void setInitialReconnectDelay(long initialReconnectDelay) {
        getConfigurationOrCreate().setInitialReconnectDelay(initialReconnectDelay);
    }

    public long getReconnectDelay() {
        return getConfigurationOrCreate().getReconnectDelay();
    }

    /**
     * Defines the interval in milliseconds between the reconnect attempts, if the connection to the SMSC was lost and the previous was not succeed.
     * @param reconnectDelay
     */
    public void setReconnectDelay(long reconnectDelay) {
        getConfigurationOrCreate().setReconnectDelay(reconnectDelay);
    }

    public boolean isLazySessionCreation() {
        return getConfigurationOrCreate().isLazySessionCreation();
    }

    /**
     * Sessions can be lazily created to avoid exceptions, if the SMSC is not available when the Camel producer is started.
     * Camel will check the in message headers 'CamelSmppSystemId' and 'CamelSmppPassword' of the first exchange.
     * If they are present, Camel will use these data to connect to the SMSC.
     * @param lazySessionCreation
     */
    public void setLazySessionCreation(boolean lazySessionCreation) {
        getConfigurationOrCreate().setLazySessionCreation(lazySessionCreation);
    }

    public String getHttpProxyHost() {
        return getConfigurationOrCreate().getHttpProxyHost();
    }

    /**
     * If you need to tunnel SMPP through a HTTP proxy, set this attribute to the hostname or ip address of your HTTP proxy.
     * @param httpProxyHost
     */
    public void setHttpProxyHost(String httpProxyHost) {
        getConfigurationOrCreate().setHttpProxyHost(httpProxyHost);
    }

    public Integer getHttpProxyPort() {
        return getConfigurationOrCreate().getHttpProxyPort();
    }

    /**
     * If you need to tunnel SMPP through a HTTP proxy, set this attribute to the port of your HTTP proxy.
     * @param httpProxyPort
     */
    public void setHttpProxyPort(Integer httpProxyPort) {
        getConfigurationOrCreate().setHttpProxyPort(httpProxyPort);
    }

    public String getHttpProxyUsername() {
        return getConfigurationOrCreate().getHttpProxyUsername();
    }

    /**
     * If your HTTP proxy requires basic authentication, set this attribute to the username required for your HTTP proxy.
     * @param httpProxyUsername
     */
    public void setHttpProxyUsername(String httpProxyUsername) {
        getConfigurationOrCreate().setHttpProxyUsername(httpProxyUsername);
    }

    public String getHttpProxyPassword() {
        return getConfigurationOrCreate().getHttpProxyPassword();
    }

    /**
     * If your HTTP proxy requires basic authentication, set this attribute to the password required for your HTTP proxy.
     * @param httpProxyPassword
     */
    public void setHttpProxyPassword(String httpProxyPassword) {
        getConfigurationOrCreate().setHttpProxyPassword(httpProxyPassword);
    }

    public SessionStateListener getSessionStateListener() {
        return getConfigurationOrCreate().getSessionStateListener();
    }

    /**
     * You can refer to a org.jsmpp.session.SessionStateListener in the Registry to receive callbacks when the session state changed.
     * @param sessionStateListener
     */
    public void setSessionStateListener(SessionStateListener sessionStateListener) {
        getConfigurationOrCreate().setSessionStateListener(sessionStateListener);
    }

    public String getAddressRange() {
        return getConfigurationOrCreate().getAddressRange();
    }

    /**
     *  You can specify the address range for the SmppConsumer as defined in section 5.2.7 of the SMPP 3.4 specification.
     *  The SmppConsumer will receive messages only from SMSC's which target an address (MSISDN or IP address) within this range.
     * @param addressRange
     */
    public void setAddressRange(String addressRange) {
        getConfigurationOrCreate().setAddressRange(addressRange);
    }

    public SmppSplittingPolicy getSplittingPolicy() {
        return getConfigurationOrCreate().getSplittingPolicy();
    }

    /**
     * You can specify a policy for handling long messages:
     * <ul>
     *     <li>ALLOW - the default, long messages are split to 140 bytes per message</li>
     *     <li>TRUNCATE - long messages are split and only the first fragment will be sent to the SMSC.
     *     Some carriers drop subsequent fragments so this reduces load on the SMPP connection sending parts of a message that will never be delivered.</li>
     *     <li>REJECT - if a message would need to be split, it is rejected with an SMPP NegativeResponseException and the reason code signifying the message is too long.</li>
     * </ul>
     * @param splittingPolicy
     */
    public void setSplittingPolicy(SmppSplittingPolicy splittingPolicy) {
        getConfigurationOrCreate().setSplittingPolicy(splittingPolicy);
    }

    /**
     * These headers will be passed to the proxy server while establishing the connection.
     * @param proxyHeaders
     */
    public void setProxyHeaders(Map<String, String> proxyHeaders) {
        getConfigurationOrCreate().setProxyHeaders(proxyHeaders);
    }

    public Map<String, String> getProxyHeaders() {
        return getConfigurationOrCreate().getProxyHeaders();
    }
}