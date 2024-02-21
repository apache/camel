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
package org.apache.camel.component.snmp;

import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.Target;
import org.snmp4j.TransportMapping;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.security.SecurityModels;
import org.snmp4j.security.USM;
import org.snmp4j.smi.Address;
import org.snmp4j.smi.GenericAddress;
import org.snmp4j.smi.Integer32;
import org.snmp4j.transport.DefaultTcpTransportMapping;
import org.snmp4j.transport.DefaultUdpTransportMapping;

/**
 * A snmp trap producer
 */
public class SnmpTrapProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(SnmpTrapProducer.class);

    private final SnmpEndpoint endpoint;

    private Address targetAddress;
    private USM usm;
    private Target target;

    public SnmpTrapProducer(SnmpEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        this.targetAddress = GenericAddress.parse(this.endpoint.getAddress());
        LOG.debug("targetAddress: {}", targetAddress);

        this.usm = SnmpHelper.createAndSetUSM(endpoint);
        this.target = SnmpHelper.createTarget(endpoint);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        try {
            if (this.usm != null) {
                SecurityModels.getInstance().removeSecurityModel(new Integer32(this.usm.getID()));
            }
        } finally {
            this.targetAddress = null;
            this.usm = null;
            this.target = null;
        }
    }

    @Override
    public void process(final Exchange exchange) throws Exception {
        // load connection data only if the endpoint is enabled
        Snmp snmp = null;
        TransportMapping<? extends Address> transport = null;

        try {
            LOG.debug("Starting SNMP Trap producer on {}", this.endpoint.getAddress());

            // either tcp or udp
            if ("tcp".equals(this.endpoint.getProtocol())) {
                transport = new DefaultTcpTransportMapping();
            } else if ("udp".equals(this.endpoint.getProtocol())) {
                transport = new DefaultUdpTransportMapping();
            } else {
                throw new IllegalArgumentException("Unknown protocol: " + this.endpoint.getProtocol());
            }

            snmp = new Snmp(transport);

            LOG.debug("SnmpTrap: getting pdu from body");
            PDU trap = exchange.getIn().getBody(PDU.class);

            trap.setErrorIndex(0);
            trap.setErrorStatus(0);
            if (this.endpoint.getSnmpVersion() == SnmpConstants.version1) {
                trap.setType(PDU.V1TRAP);
            } else {
                trap.setType(PDU.TRAP);
                trap.setMaxRepetitions(0);
            }
            LOG.debug("SnmpTrap: sending");
            snmp.send(trap, this.target);
            LOG.debug("SnmpTrap: sent");
        } finally {
            try {
                transport.close();
            } catch (Exception e) {
            }
            try {
                snmp.close();
            } catch (Exception e) {
            }
        }
    } //end process
}
