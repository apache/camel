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
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.snmp4j.PDU;
import org.snmp4j.mp.SnmpConstants;

public class SnmpEndpoint extends DefaultPollingEndpoint {

    public static final String DEFAULT_COMMUNITY = "public";
    public static final int DEFAULT_SNMP_VERSION = SnmpConstants.version1;
    public static final int DEFAULT_SNMP_RETRIES = 2;
    public static final int DEFAULT_SNMP_TIMEOUT = 1500;

    private static final Log LOG = LogFactory.getLog(SnmpEndpoint.class);

    private OIDList oids = new OIDList();
    private String address;
    private String protocol = "udp";
    private int retries = DEFAULT_SNMP_RETRIES;
    private int timeout = DEFAULT_SNMP_TIMEOUT;
    private int snmpVersion = DEFAULT_SNMP_VERSION;
    private String snmpCommunity = DEFAULT_COMMUNITY;
    private SnmpActionType type;
    private int delay = 60;

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
            return new SnmpTrapConsumer(this, processor);
        } else if (this.type == SnmpActionType.POLL) {
            return new SnmpOIDPoller(this, processor);
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
     * creates and configures the endpoint
     *
     * @throws Exception if unable to setup connection
     */
    public void initiate() throws Exception {
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
        if (LOG.isDebugEnabled()) {
            LOG.debug("Using snmp address " + address);
        }
        setAddress(address);
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
}
