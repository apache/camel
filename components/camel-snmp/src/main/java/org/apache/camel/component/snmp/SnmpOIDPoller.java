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

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.ScheduledPollConsumer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.snmp4j.CommunityTarget;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.TransportMapping;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.event.ResponseListener;
import org.snmp4j.mp.MPv3;
import org.snmp4j.security.SecurityModels;
import org.snmp4j.security.SecurityProtocols;
import org.snmp4j.security.USM;
import org.snmp4j.smi.Address;
import org.snmp4j.smi.GenericAddress;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.transport.DefaultUdpTransportMapping;

public class SnmpOIDPoller extends ScheduledPollConsumer implements ResponseListener {

    private static final transient Log LOG = LogFactory.getLog(SnmpOIDPoller.class);

    private Address targetAddress;
    private TransportMapping transport;
    private Snmp snmp;
    private USM usm;
    private CommunityTarget target;
    private PDU pdu;
    private SnmpEndpoint endpoint;

    public SnmpOIDPoller(SnmpEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
        // convert delay from seconds to millis
        setDelay(endpoint.getDelay() * 1000);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        LOG.debug("Activating oid poller");
        this.targetAddress = GenericAddress.parse(this.endpoint.getAddress());
        this.transport = new DefaultUdpTransportMapping();
        this.snmp = new Snmp(this.transport);
        this.usm = new USM(SecurityProtocols.getInstance(), new OctetString(MPv3.createLocalEngineID()), 0);
        SecurityModels.getInstance().addSecurityModel(usm);

        // setting up target
        target = new CommunityTarget();
        target.setCommunity(new OctetString(this.endpoint.getSnmpCommunity()));
        target.setAddress(targetAddress);
        target.setRetries(this.endpoint.getRetries());
        target.setTimeout(this.endpoint.getTimeout());
        target.setVersion(this.endpoint.getSnmpVersion());

        // creating PDU
        this.pdu = new PDU();
        // listen to the transport
        this.transport.listen();
    }

    @Override
    protected void doStop() throws Exception {
        // stop listening to the transport
        if (this.transport.isListening()) {
            this.transport.close();
        }

        super.doStop();
    }

    @Override
    protected void poll() throws Exception {
        this.pdu.clear();
        this.pdu.setType(PDU.GET);

        // prepare the request items
        for (OID oid : this.endpoint.getOids()) {
            this.pdu.add(new VariableBinding(oid));
        }

        // send the request
        snmp.send(pdu, target, null, this);
    }

    public void onResponse(ResponseEvent event) {
        // Always cancel async request when response has been received
        // otherwise a memory leak is created! Not canceling a request
        // immediately can be useful when sending a request to a broadcast
        // address.
        ((Snmp)event.getSource()).cancel(event.getRequest(), this);

        // check for valid response
        if (event.getRequest() == null || event.getResponse() == null) {
            // ignore null requests/responses
            LOG.debug("Received invalid snmp event. Request: " + event.getRequest() + " / Response: " + event.getResponse());
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
            LOG.debug("Received response event for " + this.endpoint.getAddress() + " : " + pdu);
        }
        Exchange exchange = endpoint.createExchange(pdu);
        try {
            getProcessor().process(exchange);
        } catch (Exception ex) {
            exchange.setException(ex);
        }
    }

    /** * @return Returns the target.
     */
    public CommunityTarget getTarget() {
        return this.target;
    }

    /**
     * @param target The target to set.
     */
    public void setTarget(CommunityTarget target) {
        this.target = target;
    }
}
