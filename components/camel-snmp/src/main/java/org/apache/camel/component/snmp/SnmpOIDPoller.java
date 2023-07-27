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

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.support.ScheduledPollConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.Target;
import org.snmp4j.TransportMapping;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.event.ResponseListener;
import org.snmp4j.smi.Address;
import org.snmp4j.smi.GenericAddress;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.transport.DefaultTcpTransportMapping;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.snmp4j.util.DefaultPDUFactory;
import org.snmp4j.util.TreeEvent;
import org.snmp4j.util.TreeUtils;

public class SnmpOIDPoller extends ScheduledPollConsumer implements ResponseListener {

    private static final Logger LOG = LoggerFactory.getLogger(SnmpOIDPoller.class);

    private Address targetAddress;
    private TransportMapping<? extends Address> transport;
    private Snmp snmp;

    private Target target;
    private PDU pdu;
    private final SnmpEndpoint endpoint;

    public SnmpOIDPoller(SnmpEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        this.targetAddress = GenericAddress.parse(this.endpoint.getAddress());

        // either tcp or udp
        if ("tcp".equals(endpoint.getProtocol())) {
            this.transport = new DefaultTcpTransportMapping();
        } else if ("udp".equals(endpoint.getProtocol())) {
            this.transport = new DefaultUdpTransportMapping();
        } else {
            throw new IllegalArgumentException("Unknown protocol: " + endpoint.getProtocol());
        }

        this.snmp = new Snmp(this.transport);
        SnmpHelper.createAndSetUSM(endpoint);
        this.pdu = SnmpHelper.createPDU(endpoint);
        this.target = SnmpHelper.createTarget(endpoint);

        // listen to the transport
        if (LOG.isDebugEnabled()) {
            LOG.debug("Starting OID poller on {} using {} protocol", endpoint.getAddress(), endpoint.getProtocol());
        }
        this.transport.listen();
        if (LOG.isInfoEnabled()) {
            LOG.info("Started OID poller on {} using {} protocol", endpoint.getAddress(), endpoint.getProtocol());
        }
    }

    @Override
    protected void doStop() throws Exception {
        // stop listening to the transport
        if (this.transport != null && this.transport.isListening()) {
            LOG.info("Stopping OID poller on {}", targetAddress);
            this.transport.close();
            LOG.info("Stopped OID poller on {}", targetAddress);
        }

        super.doStop();
    }

    @Override
    protected int poll() throws Exception {
        this.pdu.clear();

        int type = this.getPduType(this.endpoint.getType());

        this.pdu.setType(type);

        if (!endpoint.isTreeList()) {
            // prepare the request items
            for (OID oid : this.endpoint.getOids()) {
                this.pdu.add(new VariableBinding(oid));
            }
        } else {
            TreeUtils treeUtils = new TreeUtils(snmp, new DefaultPDUFactory());
            for (OID oid : this.endpoint.getOids()) {
                List<TreeEvent> events = treeUtils.getSubtree(target, new OID(oid));
                for (TreeEvent event : events) {
                    if (event == null) {
                        LOG.warn("Event is null");
                        continue;
                    }
                    if (event.isError()) {
                        LOG.error("Error in event: {}", event.getErrorMessage());
                        continue;
                    }
                    VariableBinding[] varBindings = event.getVariableBindings();
                    if (varBindings == null || varBindings.length == 0) {
                        continue;
                    }
                    for (VariableBinding varBinding : varBindings) {
                        if (varBinding == null) {
                            continue;
                        }
                        this.pdu.add(varBinding);
                    }
                }
            }
        }

        // send the request
        snmp.send(pdu, target, null, this);

        return 1;
    }

    @Override
    public void onResponse(ResponseEvent event) {
        // Always cancel async request when response has been received
        // otherwise a memory leak is created! Not canceling a request
        // immediately can be useful when sending a request to a broadcast address.
        ((Snmp) event.getSource()).cancel(event.getRequest(), this);

        // check for valid response
        if (event.getRequest() == null || event.getResponse() == null) {
            // ignore null requests/responses
            LOG.debug("Received invalid SNMP event. Request: {} / Response: {}", event.getRequest(), event.getResponse());
            return;
        }

        PDU pdu = event.getResponse();
        processPDU(pdu);
    }

    /**
     * processes the pdu message
     *
     * @param pdu the pdu
     */
    public void processPDU(PDU pdu) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Received response event for {} : {}", this.endpoint.getAddress(), pdu);
        }
        Exchange exchange = endpoint.createExchange(pdu);
        try {
            getProcessor().process(exchange);
        } catch (Exception e) {
            getExceptionHandler().handleException(e);
        }
    }

    private int getPduType(SnmpActionType type) {
        if (SnmpActionType.GET_NEXT == type) {
            return PDU.GETNEXT;
        } else {
            return PDU.GET;
        }
    }

}
