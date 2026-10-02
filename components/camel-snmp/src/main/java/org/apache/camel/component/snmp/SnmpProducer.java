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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.Target;
import org.snmp4j.TransportMapping;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.security.SecurityModels;
import org.snmp4j.security.USM;
import org.snmp4j.smi.Address;
import org.snmp4j.smi.GenericAddress;
import org.snmp4j.smi.Integer32;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.transport.DefaultTcpTransportMapping;
import org.snmp4j.transport.DefaultUdpTransportMapping;

/**
 * A snmp producer
 */
public class SnmpProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(SnmpProducer.class);

    private SnmpEndpoint endpoint;

    private Address targetAddress;
    private USM usm;
    private Target target;
    private SnmpActionType actionType;
    private PDU pdu;

    public SnmpProducer(SnmpEndpoint endpoint, SnmpActionType actionType) {
        super(endpoint);
        this.endpoint = endpoint;
        this.actionType = actionType;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        this.targetAddress = GenericAddress.parse(this.endpoint.getServerAddress());
        LOG.debug("targetAddress: {}", targetAddress);

        this.usm = SnmpHelper.createAndSetUSM(endpoint);
        this.pdu = SnmpHelper.createPDU(endpoint);
        this.target = SnmpHelper.createTarget(endpoint);

        // in here,only POLL do set the oids
        if (this.actionType == SnmpActionType.POLL) {
            for (OID oid : this.endpoint.getOids()) {
                this.pdu.add(new VariableBinding(oid));
            }
        }
        this.pdu.setErrorIndex(0);
        this.pdu.setErrorStatus(0);
        if (endpoint.getSnmpVersion() > SnmpConstants.version1) {
            this.pdu.setMaxRepetitions(0);
        }
        // support POLL and GET_NEXT
        if (this.actionType == SnmpActionType.GET_NEXT) {
            this.pdu.setType(PDU.GETNEXT);
        } else {
            this.pdu.setType(PDU.GET);
        }

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
            this.pdu = null;
        }
    }

    @Override
    public void process(final Exchange exchange) throws Exception {
        // load connection data only if the endpoint is enabled
        Snmp snmp = null;
        TransportMapping<? extends Address> transport = null;

        try {
            LOG.debug("Starting SNMP producer on {}", this.endpoint.getServerAddress());

            // either tcp or udp
            if ("tcp".equals(this.endpoint.getProtocol())) {
                transport = new DefaultTcpTransportMapping();
            } else if ("udp".equals(this.endpoint.getProtocol())) {
                transport = new DefaultUdpTransportMapping();
            } else {
                throw new IllegalArgumentException("Unknown protocol: " + this.endpoint.getProtocol());
            }

            snmp = new Snmp(transport);

            LOG.debug("Snmp: i am sending");

            snmp.listen();

            if (this.actionType == SnmpActionType.GET_NEXT) {
                // snmp walk
                List<SnmpMessage> smLst = new ArrayList<>();
                for (OID oid : this.endpoint.getOids()) {
                    this.pdu.clear();
                    this.pdu.add(new VariableBinding(oid));

                    boolean matched = true;
                    while (matched) {
                        ResponseEvent responseEvent = snmp.send(this.pdu, this.target);
                        if (responseEvent == null || responseEvent.getResponse() == null) {
                            throw new TimeoutException("SNMP Producer Timeout");
                        }
                        PDU response = responseEvent.getResponse();
                        if (response.getErrorStatus() == PDU.noSuchName) {
                            // SNMPv1 signals the end of the MIB view with noSuchName
                            break;
                        }
                        if (response.getErrorStatus() != PDU.noError) {
                            throw new CamelExchangeException(
                                    "SNMP walk of " + oid + " failed: " + response.getErrorStatusText(), exchange);
                        }
                        OID requestedOid = this.pdu.get(0).getOid();
                        VariableBinding next = null;
                        for (VariableBinding variableBinding : response.getVariableBindings()) {
                            // compare the OIDs, not their strings: 1.3.6.1.4.1.20 is not in the subtree of 1.3.6.1.4.1.2
                            if (!variableBinding.getOid().startsWith(oid)) {
                                matched = false;
                                break;
                            }
                            next = variableBinding;
                        }
                        // endOfMibView (SNMPv2c/v3) ends the walk
                        if (!matched || next == null || next.isException()) {
                            break;
                        }
                        if (next.getOid().compareTo(requestedOid) <= 0) {
                            // a walk on an OID that does not increase would not end: fail like net-snmp's snmpwalk,
                            // so that a misbehaving agent does not give a silently partial result
                            throw new CamelExchangeException(
                                    "SNMP walk of " + oid + " failed: OID not increasing: " + requestedOid + " >= "
                                                             + next.getOid(),
                                    exchange);
                        }
                        this.pdu.clear();
                        pdu.add(new VariableBinding(next.getOid()));
                        smLst.add(new SnmpMessage(getEndpoint().getCamelContext(), response));
                    }
                }
                exchange.getIn().setBody(smLst);
            } else {
                // snmp get
                ResponseEvent responseEvent = snmp.send(this.pdu, this.target);

                LOG.debug("Snmp: sended");

                if (responseEvent.getResponse() != null) {
                    exchange.getIn().setBody(new SnmpMessage(getEndpoint().getCamelContext(), responseEvent.getResponse()));
                } else {
                    throw new TimeoutException("SNMP Producer Timeout");
                }
            }
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
