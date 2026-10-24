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

import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.snmp4j.PDU;
import org.snmp4j.smi.OID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A producer without type sends a GET with the oids of the endpoint, and its body converts to the XML text.
 */
public class ProducerWithoutTypeTest extends SnmpRespondTestSupport {

    private static final String OID = "1.3.6.1.2.1.2.2.1.2";

    @ParameterizedTest
    @MethodSource("supportedVersions")
    public void testGetWithoutType(int version) {
        Object body = template.requestBody("direct:inV" + version, "");

        SnmpMessage snmpMessage = assertInstanceOf(SnmpMessage.class, body);
        // the test agent answers the next oid of the requested one (and gives the request back)
        PDU response = snmpMessage.getSnmpMessage();
        assertNotNull(response.getVariable(new OID(OID + ".1")));
        assertEquals("ether1", response.getVariable(new OID(OID + ".1")).toString());
    }

    @ParameterizedTest
    @MethodSource("supportedVersions")
    public void testConvertBodyToString(int version) {
        String body = template.requestBody("direct:textV" + version, "", String.class);

        assertTrue(body.startsWith("<snmp>"), body);
        assertTrue(body.contains("<entry><oid>" + OID + ".1</oid><value>ether1</value></entry>"), body);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:inV0").to("snmp://" + getListeningAddress() + "?oids=" + OID);
                from("direct:inV1").to("snmp://" + getListeningAddress() + "?snmpVersion=1&oids=" + OID);
                from("direct:inV3").to("snmp://" + getListeningAddress()
                                       + "?securityName=test&securityLevel=1&snmpVersion=3&oids=" + OID);

                from("direct:textV0").to("direct:inV0").convertBodyTo(String.class);
                from("direct:textV1").to("direct:inV1").convertBodyTo(String.class);
                from("direct:textV3").to("direct:inV3").convertBodyTo(String.class);
            }
        };
    }
}
