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

import java.util.concurrent.TimeUnit;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Message;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snmp4j.PDU;
import org.snmp4j.PDUv1;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.TimeTicks;
import org.snmp4j.smi.Variable;
import org.snmp4j.smi.VariableBinding;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TrapTest extends SnmpTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(TrapTest.class);

    @ParameterizedTest
    @MethodSource("supportedVersions")
    public void testSendReceiveTraps(int version) throws Exception {
        PDU trap = createTrap(version);

        // Send it
        LOG.info("Sending pdu {}", trap);
        Endpoint endpoint = context.getEndpoint("direct:snmptrapV" + version);
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setBody(trap);
        Producer producer = endpoint.createProducer();
        producer.process(exchange);

        // If all goes right it should come here
        MockEndpoint mock = getMockEndpoint("mock:resultV" + version);
        mock.expectedMessageCount(1);

        // wait a bit
        Awaitility.await().atMost(2, TimeUnit.SECONDS)
                .untilAsserted(() -> mock.assertIsSatisfied());

        Message in = mock.getReceivedExchanges().get(0).getIn();
        Assertions.assertTrue(in instanceof SnmpMessage, "Expected received object 'SnmpMessage.class'. Got: " + in.getClass());
        String msg = in.getBody(String.class);
        String expected = "<oid>1.2.3.4.5</oid><value>some string</value>";
        Assertions.assertTrue(msg.contains(expected), "Expected string containing '" + expected + "'. Got: " + msg);
    }

    private PDU createTrap(int version) {
        PDU trap = SnmpHelper.createPDU(version);

        OID oid = new OID("1.2.3.4.5");
        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, oid));
        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(5000))); // put your uptime here
        trap.add(new VariableBinding(SnmpConstants.sysDescr, new OctetString("System Description")));
        if (version == 0) {
            ((PDUv1) trap).setEnterprise(oid); //?
        }

        //Add Payload
        Variable var = new OctetString("some string");
        trap.add(new VariableBinding(oid, var));
        return trap;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                //genrate ports for trap consumers/producers
                int portV0 = AvailablePortFinder.getNextAvailable();
                int portV1 = AvailablePortFinder.getNextAvailable();
                int portV3 = AvailablePortFinder.getNextAvailable();

                from("direct:snmptrapV0")
                        .log(LoggingLevel.INFO, "Sending Trap pdu ${body}")
                        .to("snmp:127.0.0.1:" + portV0 + "?protocol=udp&type=TRAP&snmpVersion=0");

                from("snmp:0.0.0.0:" + portV0 + "?protocol=udp&type=TRAP&snmpVersion=0")
                        .to("mock:resultV0");

                from("direct:snmptrapV1")
                        .log(LoggingLevel.INFO, "Sending Trap pdu ${body}")
                        .to("snmp:127.0.0.1:" + portV1 + "?protocol=udp&type=TRAP&snmpVersion=1");

                from("snmp:0.0.0.0:" + portV1 + "?protocol=udp&type=TRAP&snmpVersion=1")
                        .to("mock:resultV1");

                from("direct:snmptrapV3")
                        .log(LoggingLevel.INFO, "Sending Trap pdu ${body}")
                        .to("snmp:127.0.0.1:" + portV3
                            + "?securityName=test&securityLevel=1&protocol=udp&type=TRAP&snmpVersion=3");

                from("snmp:0.0.0.0:" + portV3 + "?securityName=test&securityLevel=1&protocol=udp&type=TRAP&snmpVersion=3")
                        .to("mock:resultV3");
            }
        };
    }

}
