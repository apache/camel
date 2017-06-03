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

import java.util.concurrent.TimeoutException;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snmp4j.CommunityTarget;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.TransportMapping;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.MPv3;
import org.snmp4j.security.SecurityModels;
import org.snmp4j.security.SecurityProtocols;
import org.snmp4j.security.USM;
import org.snmp4j.smi.Address;
import org.snmp4j.smi.GenericAddress;
import org.snmp4j.smi.Integer32;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
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
    private CommunityTarget target;
    private PDU pdu;
    
    public SnmpProducer(SnmpEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }
    
    @Override
    protected void doStart() throws Exception {
        super.doStart();

        this.targetAddress = GenericAddress.parse(this.endpoint.getAddress());
        LOG.debug("targetAddress: {}", targetAddress);

        this.usm = new USM(SecurityProtocols.getInstance(), new OctetString(MPv3.createLocalEngineID()), 0);
        SecurityModels.getInstance().addSecurityModel(this.usm);
        
        // setting up target
        this.target = new CommunityTarget();
        this.target.setCommunity(new OctetString(endpoint.getSnmpCommunity()));
        this.target.setAddress(this.targetAddress);
        this.target.setRetries(this.endpoint.getRetries());
        this.target.setTimeout(this.endpoint.getTimeout());
        this.target.setVersion(this.endpoint.getSnmpVersion());

        this.pdu = new PDU();
        for (OID oid : this.endpoint.getOids()) {
            this.pdu.add(new VariableBinding(oid));
        }
        this.pdu.setErrorIndex(0);
        this.pdu.setErrorStatus(0);
        this.pdu.setMaxRepetitions(0);
        this.pdu.setType(PDU.GET);
        
    }
    
    @Override
    protected void doStop() throws Exception {
        super.doStop();

        try {
            SecurityModels.getInstance().removeSecurityModel(new Integer32(this.usm.getID()));
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
            LOG.debug("Starting SNMP producer on {}", this.endpoint.getAddress());
            
            // either tcp or udp
            if ("tcp".equals(this.endpoint.getProtocol())) {
                transport = new DefaultTcpTransportMapping();
            } else if ("udp".equals(this.endpoint.getProtocol())) {
                transport = new DefaultUdpTransportMapping();
            } else {
                throw new IllegalArgumentException("Unknown protocol: {} " + this.endpoint.getProtocol());
            }
    
            snmp = new Snmp(transport);
            
            LOG.debug("Snmp: i am sending");
    
            snmp.listen();
            ResponseEvent responseEvent = snmp.send(this.pdu, this.target);
            
            LOG.debug("Snmp: sended");
    
            if (responseEvent.getResponse() != null) {
                exchange.getIn().setBody(new SnmpMessage(getEndpoint().getCamelContext(), responseEvent.getResponse()));
            } else {
                throw new TimeoutException("SNMP Producer Timeout");
            }
        } finally {
            try {
                transport.close(); 
            } catch (Exception e) { }
            try {
                snmp.close(); 
            } catch (Exception e) { }
        }
    } //end process
}