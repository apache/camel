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
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultPollingEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snmp4j.CommandResponderEvent;
import org.snmp4j.PDU;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.security.SecurityLevel;

/**
 * The snmp component gives you the ability to poll SNMP capable devices or receiving traps.
 */
@UriEndpoint(firstVersion = "2.1.0", scheme = "snmp", title = "SNMP", syntax = "snmp:host:port", consumerOnly = true, label = "monitoring")
public class SnmpEndpoint extends DefaultPollingEndpoint {

    public static final String DEFAULT_COMMUNITY = "public";
    public static final int DEFAULT_SNMP_VERSION = SnmpConstants.version1;
    public static final int DEFAULT_SNMP_RETRIES = 2;
    public static final int DEFAULT_SNMP_TIMEOUT = 1500;

    private static final Logger LOG = LoggerFactory.getLogger(SnmpEndpoint.class);

    private transient String address;

    @UriPath(description = "Hostname of the SNMP enabled device") @Metadata(required = "true")
    private String host;
    @UriPath(description = "Port number of the SNMP enabled device") @Metadata(required = "true")
    private Integer port;
    @UriParam(defaultValue = "udp", enums = "tcp,udp")
    private String protocol = "udp";
    @UriParam(defaultValue = "" + DEFAULT_SNMP_RETRIES)
    private int retries = DEFAULT_SNMP_RETRIES;
    @UriParam(defaultValue = "" + DEFAULT_SNMP_TIMEOUT)
    private int timeout = DEFAULT_SNMP_TIMEOUT;
    @UriParam(defaultValue = "" + DEFAULT_SNMP_VERSION, enums = "0,1,3")
    private int snmpVersion = DEFAULT_SNMP_VERSION;
    @UriParam(defaultValue = DEFAULT_COMMUNITY)
    private String snmpCommunity = DEFAULT_COMMUNITY;
    @UriParam
    private SnmpActionType type;
    @UriParam(label = "consumer", defaultValue = "60000")
    private long delay = 60000;
    @UriParam(defaultValue = "" + SecurityLevel.AUTH_PRIV, enums = "1,2,3", label = "security")
    private int securityLevel = SecurityLevel.AUTH_PRIV;
    @UriParam(label = "security", secret = true)
    private String securityName;
    @UriParam(enums = "MD5,SHA1", label = "security")
    private String authenticationProtocol;
    @UriParam(label = "security", secret = true)
    private String authenticationPassphrase;
    @UriParam(label = "security", secret = true)
    private String privacyProtocol;
    @UriParam(label = "security", secret = true)
    private String privacyPassphrase;
    @UriParam
    private String snmpContextName;
    @UriParam
    private String snmpContextEngineId;
    @UriParam(javaType = "java.lang.String")
    private OIDList oids = new OIDList();

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
            // As the SnmpTrapConsumer is not a polling consumer we don't need to call the configureConsumer here.
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
        if (this.type == SnmpActionType.TRAP) {
            return new SnmpTrapProducer(this);
        } else {
            return new SnmpProducer(this);
        }
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
        Exchange exchange = super.createExchange();
        exchange.setIn(new SnmpMessage(getCamelContext(), pdu));
        return exchange;
    }

    /**
     * creates an exchange for the given message
     *
     * @param pdu the pdu
     * @param event a snmp4j CommandResponderEvent
     * @return an exchange
     */
    public Exchange createExchange(PDU pdu, CommandResponderEvent event) {
        Exchange exchange = super.createExchange();
        exchange.setIn(new SnmpMessage(getCamelContext(), pdu, event));
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

    public long getDelay() {
        return delay;
    }

    /**
     * Sets update rate in seconds
     *
     * @param updateEvery the update rate in seconds
     */
    @Override
    public void setDelay(long updateEvery) {
        this.delay = updateEvery;
    }

    public SnmpActionType getType() {
        return this.type;
    }

    /**
     * Which operation to perform such as poll, trap, etc.
     */
    public void setType(SnmpActionType type) {
        this.type = type;
    }

    public OIDList getOids() {
        return this.oids;
    }

    /**
     * Defines which values you are interested in. Please have a look at the Wikipedia to get a better understanding.
     * You may provide a single OID or a coma separated list of OIDs.
     * Example: oids="1.3.6.1.2.1.1.3.0,1.3.6.1.2.1.25.3.2.1.5.1,1.3.6.1.2.1.25.3.5.1.1.1,1.3.6.1.2.1.43.5.1.1.11.1"
     */
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

    /**
     * Defines how often a retry is made before canceling the request.
     */
    public void setRetries(int retries) {
        this.retries = retries;
    }

    public int getTimeout() {
        return this.timeout;
    }

    /**
     * Sets the timeout value for the request in millis.
     */
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public int getSnmpVersion() {
        return this.snmpVersion;
    }

    /**
     * Sets the snmp version for the request.
     * <p/>
     * The value 0 means SNMPv1, 1 means SNMPv2c, and the value 3 means SNMPv3
     */
    public void setSnmpVersion(int snmpVersion) {
        this.snmpVersion = snmpVersion;
    }

    public String getSnmpCommunity() {
        return this.snmpCommunity;
    }

    /**
     * Sets the community octet string for the snmp request.
     */
    public void setSnmpCommunity(String snmpCommunity) {
        this.snmpCommunity = snmpCommunity;
    }

    public String getProtocol() {
        return this.protocol;
    }

    /**
     * Here you can select which protocol to use. You can use either udp or tcp.
     */
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

    /**
     * Sets the security level for this target. The supplied security level must
     * be supported by the security model dependent information associated with
     * the security name set for this target.
     * <p/>
     * The value 1 means: No authentication and no encryption. Anyone can create and read messages with this security level
     * The value 2 means: Authentication and no encryption. Only the one with the right authentication key can create
     * messages with this security level, but anyone can read the contents of the message.
     * The value 3 means: Authentication and encryption. Only the one with the right authentication key can create messages
     * with this security level, and only the one with the right encryption/decryption key can read the contents of the message.
     */
    public void setSecurityLevel(int securityLevel) {
        this.securityLevel = securityLevel;
    }

    public String getSecurityName() {
        return securityName;
    }

    /**
     * Sets the security name to be used with this target.
     */
    public void setSecurityName(String securityName) {
        this.securityName = securityName;
    }

    public String getAuthenticationProtocol() {
        return authenticationProtocol;
    }

    /**
     * Authentication protocol to use if security level is set to enable authentication
     * The possible values are: MD5, SHA1
     */
    public void setAuthenticationProtocol(String authenticationProtocol) {
        this.authenticationProtocol = authenticationProtocol;
    }

    public String getAuthenticationPassphrase() {
        return authenticationPassphrase;
    }

    /**
     * The authentication passphrase. If not <code>null</code>, <code>authenticationProtocol</code> must also be not
     * <code>null</code>. RFC3414 11.2 requires passphrases to have a minimum length of 8 bytes.
     * If the length of <code>authenticationPassphrase</code> is less than 8 bytes an <code>IllegalArgumentException</code> is thrown.
     */
    public void setAuthenticationPassphrase(String authenticationPassphrase) {
        this.authenticationPassphrase = authenticationPassphrase;
    }

    public String getPrivacyProtocol() {
        return privacyProtocol;
    }

    /**
     * The privacy protocol ID to be associated with this user. If set to <code>null</code>, this user only supports unencrypted messages.
     */
    public void setPrivacyProtocol(String privacyProtocol) {
        this.privacyProtocol = privacyProtocol;
    }

    public String getPrivacyPassphrase() {
        return privacyPassphrase;
    }

    /**
     * The privacy passphrase. If not <code>null</code>, <code>privacyProtocol</code> must also be not <code>null</code>.
     * RFC3414 11.2 requires passphrases to have a minimum length of 8 bytes. If the length of
     * <code>authenticationPassphrase</code> is less than 8 bytes an <code>IllegalArgumentException</code> is thrown.
     */
    public void setPrivacyPassphrase(String privacyPassphrase) {
        this.privacyPassphrase = privacyPassphrase;
    }

    public String getSnmpContextName() {
        return snmpContextName;
    }

    /**
     * Sets the context name field of this scoped PDU.
     */
    public void setSnmpContextName(String snmpContextName) {
        this.snmpContextName = snmpContextName;
    }

    public String getSnmpContextEngineId() {
        return snmpContextEngineId;
    }

    /**
     * Sets the context engine ID field of the scoped PDU.
     */
    public void setSnmpContextEngineId(String snmpContextEngineId) {
        this.snmpContextEngineId = snmpContextEngineId;
    }

    @Override
    public String toString() {
        // only show address to avoid user and password details to be shown
        return "snmp://" + address;
    }
}
