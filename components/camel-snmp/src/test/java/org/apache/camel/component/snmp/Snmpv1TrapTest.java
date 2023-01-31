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

import org.apache.camel.LoggingLevel;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.snmp4j.PDU;
import org.snmp4j.PDUv1;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.TimeTicks;
import org.snmp4j.smi.Variable;
import org.snmp4j.smi.VariableBinding;

public class Snmpv1TrapTest extends AbstractTrapTest {

    @Override
    public PDU createTrap() {
        PDUv1 trap = new PDUv1();
        trap.setGenericTrap(PDUv1.ENTERPRISE_SPECIFIC);
        trap.setSpecificTrap(1);

        OID oid = new OID("1.2.3.4.5");
        trap.add(new VariableBinding(SnmpConstants.snmpTrapOID, oid));
        trap.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(5000))); // put your uptime here
        trap.add(new VariableBinding(SnmpConstants.sysDescr, new OctetString("System Description")));
        trap.setEnterprise(oid);

        //Add Payload
        Variable var = new OctetString("some string");
        trap.add(new VariableBinding(oid, var));
        return trap;
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
                                .to("snmp:127.0.0.1:1662?protocol=udp&type=TRAP&snmpVersion=0");
                    }
                },
                new RouteBuilder() {
                    public void configure() {
                        from("snmp:0.0.0.0:1662?protocol=udp&type=TRAP&snmpVersion=0")
                                .id("SnmpTrapConsumer")
                                .to("mock:result");
                    }
                }
        };
    }
}
