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
package org.apache.camel.component.snmp;

import java.net.URI;

import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.DefaultPollingEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snmp4j.CommandResponderEvent;
import org.snmp4j.PDU;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.security.SecurityLevel;

@UriEndpoint(scheme = "snmp", consumerOnly = true, label = "monitoring")
public class SnmpEndpoint extends DefaultPollingEndpoint {

    public static final String DEFAULT_COMMUNITY = "public";
    public static final int DEFAULT_SNMP_VERSION = SnmpConstants.version1;
    public static final int DEFAULT_SNMP_RETRIES = 2;
    public static final int DEFAULT_SNMP_TIMEOUT = 1500;

    private static final Logger LOG = LoggerFactory.getLogger(SnmpEndpoint.class);

    private OIDList oids = new OIDList();

    private transient String address;

    @UriPath
    private String protocol = "udp";
    @UriPath
    private String host;
    @UriPath
    private Integer port;
    @UriParam(defaultValue = "" + DEFAULT_SNMP_RETRIES)
    private int retries = DEFAULT_SNMP_RETRIES;
    @UriParam(defaultValue = "" + DEFAULT_SNMP_TIMEOUT)
    private int timeout = DEFAULT_SNMP_TIMEOUT;
    @UriParam(defaultValue = "" + DEFAULT_SNMP_VERSION)
    private int snmpVersion = DEFAULT_SNMP_VERSION;
    @UriParam(defaultValue = DEFAULT_COMMUNITY)
    private String snmpCommunity = DEFAULT_COMMUNITY;
    @UriParam
    private SnmpActionType type;
    @UriParam(defaultValue = "60")
    private int delay = 60;

    @UriParam(defaultValue = "" + SecurityLevel.AUTH_PRIV)
    private int securityLevel = SecurityLevel.AUTH_PRIV;
    @UriParam
    private String securityName;
    @UriParam
    private String authenticationProtocol;
    @UriParam
    private String authenticationPassphrase;
    @UriParam
    private String privacyProtocol;
    @UriParam
    private String privacyPassphrase;
    @UriParam
    private String snmpContextName;
    @UriParam
    private String snmpContextEngineId;
    /**
     * creates a snmp endpoint
     *
     * @param uri       the endpoint uri
     * @param component the component
     */
    public SnmpEndpoint(String uri, SnmpComponent component) {
        super(uri, component);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        if (this.type == SnmpActionType.TRAP) {
            SnmpTrapConsumer answer = new SnmpTrapConsumer(this, processor);
            configureConsumer(answer);
            return answer;
        } else if (this.type == SnmpActionType.POLL) {
            SnmpOIDPoller answer = new SnmpOIDPoller(this, processor);
            configureConsumer(answer);
            return answer;
        } else {
            throw new IllegalArgumentException("The type '" + this.type + "' is not valid!");
        }
    }

    public Producer createProducer() throws Exception {
        throw new UnsupportedOperationException("SnmpProducer is not implemented");
    }

    public boolean isSingleton() {
        return true;
    }

    /**
     * creates an exchange for the given message
     *
     * @param pdu the pdu
     * @return an exchange
     */
    public Exchange createExchange(PDU pdu) {
        return createExchange(getExchangePattern(), pdu);
    }

    /**
     * creates an exchange for the given message
     *
     * @param pdu the pdu
     * @param event a snmp4j CommandResponderEvent
     * @return an exchange
     */
    public Exchange createExchange(PDU pdu, CommandResponderEvent event) {
        return createExchange(getExchangePattern(), pdu, event);
    }

    /**
     * creates an exchange for the given pattern and message
     *
     * @param pattern the message exchange pattern
     * @param pdu     the pdu
     * @return the exchange
     */
    private Exchange createExchange(ExchangePattern pattern, PDU pdu) {
        Exchange exchange = new DefaultExchange(this, pattern);
        exchange.setIn(new SnmpMessage(pdu));
        return exchange;
    }

    /**
     * creates an exchange for the given pattern and message
     *
     * @param pattern the message exchange pattern
     * @param pdu     the pdu
     * @param event   a snmp4j CommandResponderEvent
     * @return the exchange
     */
    private Exchange createExchange(ExchangePattern pattern, PDU pdu, CommandResponderEvent event) {
        Exchange exchange = new DefaultExchange(this, pattern);
        exchange.setIn(new SnmpMessage(pdu, event));
        return exchange;
    }

    /**
     * creates and configures the endpoint
     *
     * @throws Exception if unable to setup connection
     * @deprecated use {@link #start()} instead
     */
    @Deprecated
    public void initiate() throws Exception {
        // noop
    }

    public int getDelay() {
        return delay;
    }

    /**
     * Sets update rate in seconds
     *
     * @param updateEvery the update rate in seconds
     */
    public void setDelay(int updateEvery) {
        this.delay = updateEvery;
    }

    public SnmpActionType getType() {
        return this.type;
    }

    public void setType(SnmpActionType type) {
        this.type = type;
    }

    public OIDList getOids() {
        return this.oids;
    }

    public void setOids(OIDList oids) {
        this.oids = oids;
    }

    public String getAddress() {
        return this.address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public int getRetries() {
        return this.retries;
    }

    public void setRetries(int retries) {
        this.retries = retries;
    }

    public int getTimeout() {
        return this.timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public int getSnmpVersion() {
        return this.snmpVersion;
    }

    public void setSnmpVersion(int snmpVersion) {
        this.snmpVersion = snmpVersion;
    }

    public String getSnmpCommunity() {
        return this.snmpCommunity;
    }

    public void setSnmpCommunity(String snmpCommunity) {
        this.snmpCommunity = snmpCommunity;
    }

    public String getProtocol() {
        return this.protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        URI uri = URI.create(getEndpointUri());
        String host = uri.getHost();
        int port = uri.getPort();
        if (host == null || host.trim().length() < 1) {
            host = "127.0.0.1";
        }
        if (port == -1) {
            if (getType() == SnmpActionType.POLL) {
                port = 161; // default snmp poll port
            } else {
                port = 162; // default trap port
            }
        }


        // set the address
        String address = String.format("%s:%s/%d", getProtocol(), host, port);
        LOG.debug("Using snmp address {}", address);
        setAddress(address);
    }

    public int getSecurityLevel() {
        return securityLevel;
    }

    public void setSecurityLevel(int securityLevel) {
        this.securityLevel = securityLevel;
    }

    public String getSecurityName() {
        return securityName;
    }

    public void setSecurityName(String securityName) {
        this.securityName = securityName;
    }

    public String getAuthenticationProtocol() {
        return authenticationProtocol;
    }

    public void setAuthenticationProtocol(String authenticationProtocol) {
        this.authenticationProtocol = authenticationProtocol;
    }

    public String getAuthenticationPassphrase() {
        return authenticationPassphrase;
    }

    public void setAuthenticationPassphrase(String authenticationPassphrase) {
        this.authenticationPassphrase = authenticationPassphrase;
    }

    public String getPrivacyProtocol() {
        return privacyProtocol;
    }

    public void setPrivacyProtocol(String privacyProtocol) {
        this.privacyProtocol = privacyProtocol;
    }

    public String getPrivacyPassphrase() {
        return privacyPassphrase;
    }

    public void setPrivacyPassphrase(String privacyPassphrase) {
        this.privacyPassphrase = privacyPassphrase;
    }

    public String getSnmpContextName() {
        return snmpContextName;
    }

    public void setSnmpContextName(String snmpContextName) {
        this.snmpContextName = snmpContextName;
    }

    public String getSnmpContextEngineId() {
        return snmpContextEngineId;
    }

    public void setSnmpContextEngineId(String snmpContextEngineId) {
        this.snmpContextEngineId = snmpContextEngineId;
    }

    @Override
    public String toString() {
        // only show address to avoid user and password details to be shown
        return "SnmpEndpoint[" + address + "]";
    }
}
