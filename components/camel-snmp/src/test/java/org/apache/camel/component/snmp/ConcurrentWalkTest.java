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

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.snmp4j.CommandResponder;
import org.snmp4j.CommandResponderEvent;
import org.snmp4j.MessageException;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.mp.StatusInformation;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.UdpAddress;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.transport.DefaultUdpTransportMapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two walks on the same endpoint at the same time: each must get the variables of the walked subtrees, also when one of
 * its requests has to be sent again (SNMP4J sends the request PDU again after the timeout).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ConcurrentWalkTest extends SnmpTestSupport {

    private static final OID ALPHA = new OID("1.3.6.1.4.1.9999.1");
    private static final OID BETA = new OID("1.3.6.1.4.1.9999.2");

    // the agent's MIB: the answer to GETNEXT of each OID
    private static final Map<OID, VariableBinding> NEXT = Map.of(
            ALPHA, new VariableBinding(new OID("1.3.6.1.4.1.9999.1.1"), new OctetString("a1")),
            new OID("1.3.6.1.4.1.9999.1.1"), new VariableBinding(new OID("1.3.6.1.4.1.9999.1.2"), new OctetString("a2")),
            new OID("1.3.6.1.4.1.9999.1.2"), new VariableBinding(new OID("1.3.6.1.4.1.9999.2.1"), new OctetString("b1")),
            BETA, new VariableBinding(new OID("1.3.6.1.4.1.9999.2.1"), new OctetString("b1")),
            new OID("1.3.6.1.4.1.9999.2.1"), new VariableBinding(new OID("1.3.6.1.4.1.9999.2.2"), new OctetString("b2")),
            new OID("1.3.6.1.4.1.9999.2.2"), new VariableBinding(new OID("1.3.6.1.4.1.9999.3.1"), new OctetString("c1")));

    private final AtomicBoolean firstRequest = new AtomicBoolean(true);
    private final CountDownLatch firstRequestDropped = new CountDownLatch(1);
    private Snmp agent;
    private String agentAddress;

    @BeforeAll
    public void startAgent() throws IOException {
        DefaultUdpTransportMapping transport = new DefaultUdpTransportMapping(new UdpAddress("127.0.0.1/0"));
        agent = new Snmp(transport);
        agent.addCommandResponder(new Agent());
        agent.listen();
        agentAddress = transport.getListenAddress().toString().replaceFirst("/", ":");
    }

    @AfterAll
    public void stopAgent() throws IOException {
        if (agent != null) {
            agent.close();
        }
    }

    @Test
    public void testConcurrentWalks() throws Exception {
        // the agent does not answer the first request of the first walk, which SNMP4J sends again after the timeout;
        // the second walk on the same endpoint runs completely in the meantime
        CompletableFuture<List<?>> first
                = CompletableFuture.supplyAsync(() -> template.requestBody("direct:walk", null, List.class));
        assertTrue(firstRequestDropped.await(10, TimeUnit.SECONDS));

        List<?> second = template.requestBody("direct:walk", null, List.class);

        assertEquals(List.of("a1", "a2", "b1", "b2"), values(second));
        assertEquals(List.of("a1", "a2", "b1", "b2"), values(first.get(20, TimeUnit.SECONDS)));
    }

    private static List<String> values(List<?> messages) {
        return messages.stream()
                .map(message -> ((SnmpMessage) message).getSnmpMessage().get(0).getVariable().toString())
                .toList();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:walk")
                        .to("snmp:" + agentAddress + "?protocol=udp&type=GET_NEXT&timeout=2000&retries=1&oids="
                            + ALPHA + "," + BETA);
            }
        };
    }

    private final class Agent implements CommandResponder {

        @Override
        public synchronized void processPdu(CommandResponderEvent event) {
            PDU request = event.getPDU();
            if (firstRequest.getAndSet(false)) {
                firstRequestDropped.countDown();
                return;
            }
            VariableBinding next = NEXT.get(request.get(0).getOid());
            PDU response = (PDU) request.clone();
            response.setType(PDU.RESPONSE);
            if (next != null) {
                response.set(0, next);
            } else {
                // end of the MIB view (SNMPv1)
                response.setErrorStatus(PDU.noSuchName);
                response.setErrorIndex(1);
            }
            try {
                event.getMessageDispatcher().returnResponsePdu(
                        event.getMessageProcessingModel(), event.getSecurityModel(), event.getSecurityName(),
                        event.getSecurityLevel(), response, event.getMaxSizeResponsePDU(), event.getStateReference(),
                        new StatusInformation());
            } catch (MessageException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
