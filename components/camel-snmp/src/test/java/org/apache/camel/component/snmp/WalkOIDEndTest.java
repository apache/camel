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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.snmp4j.CommandResponder;
import org.snmp4j.CommandResponderEvent;
import org.snmp4j.MessageException;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.mp.StatusInformation;
import org.snmp4j.smi.Null;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.UdpAddress;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.transport.DefaultUdpTransportMapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A GET_NEXT walk must stop at the end of the MIB view and at the end of the subtree, and fail when the agent does not
 * answer or answers with an error.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class WalkOIDEndTest extends SnmpTestSupport {

    // nothing listens on this port
    @RegisterExtension
    static AvailablePortFinder.Port unusedPort = AvailablePortFinder.find();

    // the GETNEXT answers of the agent: requested OID -> next OID and value (endOfMibView repeats the requested OID)
    private static final Map<String, VariableBinding> NEXT = Map.of(
            "1.3.6.1.4.1.9", new VariableBinding(new OID("1.3.6.1.4.1.9.1.0"), new OctetString("cisco")),
            "1.3.6.1.4.1.9.1.0", new VariableBinding(new OID("1.3.6.1.4.1.9.1.0"), Null.endOfMibView),
            "1.3.6.1.4.1.2", new VariableBinding(new OID("1.3.6.1.4.1.2.1.0"), new OctetString("ibm")),
            "1.3.6.1.4.1.2.1.0", new VariableBinding(new OID("1.3.6.1.4.1.2021.1.0"), new OctetString("ucd")),
            "1.3.6.1.4.1.2021.1.0", new VariableBinding(new OID("1.3.6.1.4.1.3.1.0"), new OctetString("other")),
            "1.3.6.1.4.1.11", new VariableBinding(new OID("1.3.6.1.4.1.11.1.0"), new OctetString("hp")));

    // the GETNEXT requests the agent answers with an error status and the requested variable binding:
    // noSuchName is how an SNMPv1 agent signals the end of the MIB view
    private static final Map<String, Integer> ERRORS = Map.of(
            "1.3.6.1.4.1.11.1.0", PDU.noSuchName,
            "1.3.6.1.4.1.12", PDU.genErr);

    // bounds the requests for one OID, so that a walk that does not stop ends anyway
    private static final int MAX_REQUESTS_PER_OID = 20;

    private final Map<String, AtomicInteger> requests = new ConcurrentHashMap<>();
    private Snmp agent;
    private String agentAddress;

    @BeforeAll
    public void startAgent() throws Exception {
        DefaultUdpTransportMapping transport = new DefaultUdpTransportMapping(new UdpAddress("127.0.0.1/0"));
        agent = new Snmp(transport);
        agent.addCommandResponder(new CommandResponder() {
            @Override
            public void processPdu(CommandResponderEvent event) {
                PDU request = event.getPDU();
                if (request.getType() != PDU.GETNEXT) {
                    return;
                }
                String oid = request.get(0).getOid().toDottedString();
                boolean bounded
                        = requests.computeIfAbsent(oid, k -> new AtomicInteger()).incrementAndGet() > MAX_REQUESTS_PER_OID;
                VariableBinding next = NEXT.get(oid);
                Integer errorStatus = ERRORS.get(oid);
                PDU response = (PDU) request.clone();
                response.setType(PDU.RESPONSE);
                // clear() also resets the request id, which the response must carry
                response.clear();
                response.setRequestID(request.getRequestID());
                if (errorStatus != null && !bounded) {
                    response.add(request.get(0));
                    response.setErrorStatus(errorStatus);
                    response.setErrorIndex(1);
                } else {
                    if (next == null || bounded) {
                        next = new VariableBinding(new OID("1.3.6.1.6.1.0"), new OctetString("beyond"));
                    }
                    response.add(next);
                }
                try {
                    agent.getMessageDispatcher().returnResponsePdu(event.getMessageProcessingModel(),
                            event.getSecurityModel(), event.getSecurityName(), event.getSecurityLevel(), response,
                            event.getMaxSizeResponsePDU(), event.getStateReference(), new StatusInformation());
                } catch (MessageException e) {
                    throw new IllegalStateException(e);
                }
            }
        });
        agent.listen();
        agentAddress = transport.getListenAddress().toString().replaceFirst("/", ":");
    }

    @AfterAll
    public void stopAgent() throws Exception {
        if (agent != null) {
            agent.close();
        }
    }

    @Test
    public void testWalkStopsAtEndOfMibView() {
        List<?> result = walk("direct:endOfMib");

        assertEquals(1, result.size(), "the walk must stop at endOfMibView, got " + result);
    }

    @Test
    public void testWalkStopsAtEndOfSubtree() {
        List<?> result = walk("direct:subtree");

        assertEquals(1, result.size(), "1.3.6.1.4.1.2021 is not in the subtree of 1.3.6.1.4.1.2, got " + result);
    }

    @Test
    public void testSnmpV1WalkStopsAtNoSuchName() {
        List<?> result = walk("direct:v1EndOfMib");

        assertEquals(1, result.size(), "the walk must stop at noSuchName, got " + result);
    }

    @Test
    public void testWalkWithAgentErrorFails() {
        Exchange out = template.request("direct:agentError", e -> e.getIn().setBody(""));

        assertInstanceOf(CamelExchangeException.class, out.getException(), "got " + out.getMessage().getBody());
    }

    @Test
    public void testWalkWithoutAnswerFails() {
        Exchange out = template.request("direct:noAgent", e -> e.getIn().setBody(""));

        assertInstanceOf(TimeoutException.class, out.getException(), "got " + out.getMessage().getBody());
    }

    private List<?> walk(String uri) {
        Exchange out = template.request(uri, e -> e.getIn().setBody(""));
        assertNull(out.getException());
        return out.getMessage().getBody(List.class);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:endOfMib")
                        .toF("snmp:%s?protocol=udp&snmpVersion=1&type=GET_NEXT&oids=1.3.6.1.4.1.9", agentAddress);

                from("direct:subtree")
                        .toF("snmp:%s?protocol=udp&snmpVersion=1&type=GET_NEXT&oids=1.3.6.1.4.1.2", agentAddress);

                from("direct:v1EndOfMib")
                        .toF("snmp:%s?protocol=udp&snmpVersion=0&type=GET_NEXT&oids=1.3.6.1.4.1.11", agentAddress);

                from("direct:agentError")
                        .toF("snmp:%s?protocol=udp&snmpVersion=1&type=GET_NEXT&oids=1.3.6.1.4.1.12", agentAddress);

                from("direct:noAgent")
                        .toF("snmp:127.0.0.1:%d?protocol=udp&snmpVersion=1&type=GET_NEXT&oids=1.3.6.1.4.1.9"
                             + "&timeout=200&retries=0",
                                unusedPort.getPort());
            }
        };
    }
}
