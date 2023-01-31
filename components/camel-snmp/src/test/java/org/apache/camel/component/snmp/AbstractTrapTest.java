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
import org.apache.camel.Producer;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snmp4j.PDU;
import org.snmp4j.smi.VariableBinding;

import static org.junit.jupiter.api.Assertions.assertEquals;

public abstract class AbstractTrapTest extends CamelTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractTrapTest.class);

    protected abstract PDU createTrap();

    @Test
    public void testSendReceiveTraps() throws Exception {
        PDU trap = createTrap();

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
        assertEquals(trap, receivedTrap);
        if (LOG.isInfoEnabled()) {
            LOG.info("Received SNMP TRAP:");
            Vector<? extends VariableBinding> variableBindings = receivedTrap.getVariableBindings();
            for (VariableBinding vb : variableBindings) {
                LOG.info("  " + vb.toString());
            }
        }
    }
}
