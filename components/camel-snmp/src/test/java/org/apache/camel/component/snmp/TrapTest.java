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
import java.util.Vector;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Producer;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snmp4j.PDU;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.TimeTicks;
import org.snmp4j.smi.Variable;
import org.snmp4j.smi.VariableBinding;

/**
 * This test covers both producing and consuming snmp traps
 */
public class TrapTest extends CamelTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(TrapTest.class);

    @Test
    public void testStartRoute() throws Exception {
        // do nothing here , just make sure the camel route can started.
    }

    @Test
    public void testSendReceiveTraps() throws Exception {
        // Create a trap PDU
        PDU trap = new PDU();
        trap.setType(PDU.TRAP);

        OID oid = new OID("1.2.3.4.5");
        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, oid));
        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(5000))); // put your uptime here
        trap.add(new VariableBinding(SnmpConstants.sysDescr, new OctetString("System Description"))); 

        //Add Payload
        Variable var = new OctetString("some string");          
        trap.add(new VariableBinding(oid, var));                  
        
        // Send it
        LOG.info("Sending pdu " + trap);
        Endpoint endpoint = context.getEndpoint("direct:snmptrap");
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setBody(trap);
        Producer producer = endpoint.createProducer();
        producer.process(exchange);

        synchronized (this) {
            Thread.sleep(1000);
        }
        
        // If all goes right it should come here
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.assertIsSatisfied();

        List<Exchange> exchanges = mock.getExchanges();
        SnmpMessage msg = (SnmpMessage) exchanges.get(0).getIn();
        PDU receivedTrap = msg.getSnmpMessage();
        Assert.assertEquals(trap, receivedTrap);
        if (LOG.isInfoEnabled()) {
            LOG.info("Received SNMP TRAP:");
            Vector<? extends VariableBinding> variableBindings = receivedTrap.getVariableBindings();
            for (VariableBinding vb : variableBindings) {
                LOG.info("  " + vb.toString());
            }
        }
    }
    
    /**
     * RouteBuilders for the SNMP TRAP producer and consumer
     */
    @Override
    protected RoutesBuilder[] createRouteBuilders() {
        return new RoutesBuilder[] {
            new RouteBuilder() {
                public void configure() {
                    from("direct:snmptrap")
                        .log(LoggingLevel.INFO, "Sending Trap pdu ${body}")
                        .to("snmp:127.0.0.1:1662?protocol=udp&type=TRAP&snmpVersion=" + SnmpConstants.version2c);
                }
            },
            new RouteBuilder() {
                public void configure() {
                    from("snmp:0.0.0.0:1662?protocol=udp&type=TRAP&snmpVersion=" + SnmpConstants.version2c)
                        .id("SnmpTrapConsumer")
                        .to("mock:result");
                }
            }
        };
    }
}
