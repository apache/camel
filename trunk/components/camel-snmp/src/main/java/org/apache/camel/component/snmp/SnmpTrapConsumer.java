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
import org.apache.camel.impl.DefaultConsumer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.snmp4j.CommandResponder;
import org.snmp4j.CommandResponderEvent;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.TransportMapping;
import org.snmp4j.smi.Address;
import org.snmp4j.smi.GenericAddress;
import org.snmp4j.smi.UdpAddress;
import org.snmp4j.transport.DefaultUdpTransportMapping;

public class SnmpTrapConsumer extends DefaultConsumer implements CommandResponder {

    private static final Log LOG = LogFactory.getLog(SnmpTrapConsumer.class);
    
    private SnmpEndpoint endpoint;
    private Address listenGenericAddress;
    private Snmp snmp;
    private TransportMapping transport;

    public SnmpTrapConsumer(SnmpEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }
    
    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // load connection data only if the endpoint is enabled
        if (LOG.isInfoEnabled()) {
            LOG.info("Starting trap consumer on " + this.endpoint.getAddress());
        }
        this.listenGenericAddress = GenericAddress.parse(this.endpoint.getAddress());
        this.transport = new DefaultUdpTransportMapping((UdpAddress)this.listenGenericAddress);
        this.snmp = new Snmp(transport);
        this.snmp.addCommandResponder(this);
        
        // listen to the transport
        this.transport.listen();
    }

    @Override
    protected void doStop() throws Exception {
        // stop listening to the transport
        if (this.transport != null && this.transport.isListening()) {
            if (LOG.isInfoEnabled()) {
                LOG.info("Stopping trap consumer on " + this.endpoint.getAddress());
            }
            this.transport.close();
        }
        
        super.doStop();
    }

    public void processPdu(CommandResponderEvent event) {
        PDU pdu = event.getPDU();
        // check PDU not null
        if (pdu != null) {
            processPDU(pdu);
        } else {
            LOG.debug("Received invalid trap PDU: " + pdu);
        }
    }
    
    public void processPDU(PDU pdu) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Received trap event for " + this.endpoint.getAddress() + " : " + pdu);
        }
        Exchange exchange = endpoint.createExchange(pdu);
        try {
            getProcessor().process(exchange);
        } catch (Exception ex) {
            exchange.setException(ex);
        }
    }
}
