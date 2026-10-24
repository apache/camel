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

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class UriConfigurationTest {
    protected CamelContext context = new DefaultCamelContext();

    @Test
    public void testTrapReceiverConfiguration() {
        context.start();

        Endpoint endpoint = context.getEndpoint("snmp:0.0.0.0:1662?protocol=udp&type=TRAP&oids=1.3.6.1.2.1.7.5.1");
        assertTrue(endpoint instanceof SnmpEndpoint, "Endpoint not an SnmpEndpoint: " + endpoint);
        SnmpEndpoint snmpEndpoint = (SnmpEndpoint) endpoint;

        assertEquals(SnmpActionType.TRAP, snmpEndpoint.getType());
        assertEquals("1.3.6.1.2.1.7.5.1", snmpEndpoint.getOids().get(0).toString());
        assertEquals("udp:0.0.0.0/1662", snmpEndpoint.getServerAddress());
    }

    @Test
    public void testTrapReceiverWithoutPortConfiguration() {
        context.start();

        Endpoint endpoint = context.getEndpoint("snmp:0.0.0.0?protocol=udp&type=TRAP&oids=1.3.6.1.2.1.7.5.1");
        assertTrue(endpoint instanceof SnmpEndpoint, "Endpoint not an SnmpEndpoint: " + endpoint);
        SnmpEndpoint snmpEndpoint = (SnmpEndpoint) endpoint;

        assertEquals(SnmpActionType.TRAP, snmpEndpoint.getType());
        assertEquals("1.3.6.1.2.1.7.5.1", snmpEndpoint.getOids().get(0).toString());
        assertEquals("udp:0.0.0.0/162", snmpEndpoint.getServerAddress());
    }

    @Test
    public void testOidPollerConfiguration() {
        context.start();

        Endpoint endpoint = context.getEndpoint("snmp:127.0.0.1:1662?protocol=udp&type=POLL&oids=1.3.6.1.2.1.7.5.1");
        assertTrue(endpoint instanceof SnmpEndpoint, "Endpoint not an SnmpEndpoint: " + endpoint);
        SnmpEndpoint snmpEndpoint = (SnmpEndpoint) endpoint;

        assertEquals(SnmpActionType.POLL, snmpEndpoint.getType());
        assertEquals("1.3.6.1.2.1.7.5.1", snmpEndpoint.getOids().get(0).toString());
        assertEquals("udp:127.0.0.1/1662", snmpEndpoint.getServerAddress());
    }

    @Test
    public void testProducerWithoutTypeAndPortConfiguration() {
        context.start();

        SnmpEndpoint snmpEndpoint = context.getEndpoint("snmp:127.0.0.1?oids=1.3.6.1.2.1.1.1.0", SnmpEndpoint.class);

        assertNull(snmpEndpoint.getType());
        assertEquals("udp:127.0.0.1/161", snmpEndpoint.getServerAddress());
    }

    @Test
    public void testGetNextWithoutPortConfiguration() {
        context.start();

        SnmpEndpoint snmpEndpoint
                = context.getEndpoint("snmp:127.0.0.1?type=GET_NEXT&oids=1.3.6.1.2.1.1.1.0", SnmpEndpoint.class);

        assertEquals(SnmpActionType.GET_NEXT, snmpEndpoint.getType());
        assertEquals("udp:127.0.0.1/161", snmpEndpoint.getServerAddress());
    }

    @Test
    public void testConsumerWithGetNextFails() {
        context.start();

        SnmpEndpoint snmpEndpoint
                = context.getEndpoint("snmp:127.0.0.1?type=GET_NEXT&oids=1.3.6.1.2.1.1.1.0", SnmpEndpoint.class);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> snmpEndpoint.createConsumer(exchange -> {
                }));
        assertTrue(e.getMessage().contains("TRAP or POLL"), e.getMessage());
    }
}
