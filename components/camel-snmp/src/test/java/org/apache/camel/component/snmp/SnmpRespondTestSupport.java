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
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.snmp4j.CommandResponder;
import org.snmp4j.CommandResponderEvent;
import org.snmp4j.MessageException;
import org.snmp4j.PDU;
import org.snmp4j.PDUv1;
import org.snmp4j.ScopedPDU;
import org.snmp4j.Snmp;
import org.snmp4j.mp.CounterSupport;
import org.snmp4j.mp.DefaultCounterListener;
import org.snmp4j.mp.MPv3;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.mp.StatusInformation;
import org.snmp4j.security.AuthSHA;
import org.snmp4j.security.SecurityModels;
import org.snmp4j.security.SecurityProtocols;
import org.snmp4j.security.USM;
import org.snmp4j.security.UsmUser;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.UdpAddress;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.transport.DefaultUdpTransportMapping;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SnmpRespondTestSupport extends SnmpTestSupport {
    static final String SECURITY_NAME = "test";
    private static final String LOCAL_ADDRESS = "127.0.0.1/0";

    Snmp snmpResponder;
    String listeningAddress;

    @BeforeAll
    public void beforeAll() {
        SecurityProtocols.getInstance().addDefaultProtocols();
        DefaultUdpTransportMapping udpTransportMapping;
        try {
            udpTransportMapping = new DefaultUdpTransportMapping(new UdpAddress(LOCAL_ADDRESS));
            snmpResponder = new Snmp(udpTransportMapping);

            TestCommandResponder responder = new TestCommandResponder(snmpResponder);
            snmpResponder.addCommandResponder(responder);

            SecurityModels respSecModels = new SecurityModels() {
            };

            CounterSupport.getInstance().addCounterListener(new DefaultCounterListener());
            MPv3 mpv3CR = (MPv3) snmpResponder.getMessageDispatcher().getMessageProcessingModel(MPv3.ID);
            mpv3CR.setLocalEngineID(MPv3.createLocalEngineID(new OctetString("responder")));
            respSecModels.addSecurityModel(new USM(
                    SecurityProtocols.getInstance(),
                    new OctetString(mpv3CR.getLocalEngineID()), 0));
            mpv3CR.setSecurityModels(respSecModels);

            snmpResponder.getUSM().addUser(
                    new UsmUser(
                            new OctetString(SECURITY_NAME), AuthSHA.ID, new OctetString("changeit"),
                            AuthSHA.ID, new OctetString("changeit")));

            snmpResponder.listen();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        listeningAddress = udpTransportMapping.getListenAddress().toString().replaceFirst("/", ":");
    }

    @AfterAll
    public void afterAll(/*ExtensionContext context*/) {
        if (snmpResponder != null) {
            try {
                snmpResponder.close();
            } catch (IOException e) {
                //nothing
            }
        }
    }

    static class TestCommandResponder implements CommandResponder {

        private final Snmp commandResponder;
        private final Map<String, Integer> counts = new ConcurrentHashMap<>();

        public TestCommandResponder(Snmp commandResponder) {
            this.commandResponder = commandResponder;
        }

        @Override
        public synchronized void processPdu(CommandResponderEvent event) {
            PDU pdu = event.getPDU();
            Vector<? extends VariableBinding> vbs;
            if (pdu.getVariableBindings() != null) {
                vbs = new Vector<>(pdu.getVariableBindings());
            } else {
                vbs = new Vector<>(0);
            }
            String key = vbs.stream().sequential().map(vb -> vb.getOid().toString()).collect(Collectors.joining(","));

            int version;
            //differ snmp versions
            if (pdu instanceof PDUv1) {
                version = SnmpConstants.version1;
                key = "v1_" + key;
            } else if (pdu instanceof ScopedPDU) {
                version = SnmpConstants.version3;
                key = "v3_" + key;
            } else {
                version = SnmpConstants.version2c;
                key = "v2_" + key;
            }
            int numberOfSent = counts.getOrDefault(key, 0);

            try {
                PDU response = makeResponse(pdu, ++numberOfSent, version, vbs);
                if (response != null) {
                    response.setRequestID(pdu.getRequestID());
                    commandResponder.getMessageDispatcher().returnResponsePdu(
                            event.getMessageProcessingModel(), event.getSecurityModel(),
                            event.getSecurityName(), event.getSecurityLevel(),
                            response, event.getMaxSizeResponsePDU(),
                            event.getStateReference(), new StatusInformation());
                }
            } catch (MessageException e) {
                Assertions.assertNull(e);
            }
            counts.put(key, numberOfSent);
        }

        private PDU makeResponse(PDU originalPDU, int counter, int version, Vector<? extends VariableBinding> vbs) {
            PDU responsePDU = (PDU) originalPDU.clone();
            responsePDU.setType(PDU.RESPONSE);
            responsePDU.setErrorStatus(PDU.noError);
            responsePDU.setErrorIndex(0);
            if (vbs.isEmpty()) {
                VariableBinding vb = generateResponseBinding(counter, version, SnmpConstants.sysDescr);
                if (vb != null) {
                    responsePDU.add(vb);
                }
            } else {
                vbs.stream().forEach(vb -> responsePDU.add(generateResponseBinding(counter, version, vb.getOid())));
            }
            return responsePDU;
        }

        private VariableBinding generateResponseBinding(int counter, int version, OID oid) {
            //real responses
            //1.3.6.1.2.1.2.2.1.2 -> 1.3.6.1.2.1.2.2.1.2.1 = ether1
            if ("1.3.6.1.2.1.2.2.1.2".equals(oid.toString())) {
                return new VariableBinding(new OID("1.3.6.1.2.1.2.2.1.2.1"), new OctetString("ether1"));
            }
            //1.3.6.1.2.1.2.2.1.2.1 -> 1.3.6.1.2.1.2.2.1.2.2 = ether2
            if ("1.3.6.1.2.1.2.2.1.2.1".equals(oid.toString())) {
                return new VariableBinding(new OID("1.3.6.1.2.1.2.2.1.2.2"), new OctetString("ether2"));
            }
            //1.3.6.1.2.1.2.2.1.2.2 -> 1.3.6.1.2.1.2.2.1.2.3 = ether3
            if ("1.3.6.1.2.1.2.2.1.2.2".equals(oid.toString())) {
                return new VariableBinding(new OID("1.3.6.1.2.1.2.2.1.2.3"), new OctetString("ether3"));
            }
            //1.3.6.1.2.1.2.2.1.2.3 -> 1.3.6.1.2.1.2.2.1.2.4 = ether4
            if ("1.3.6.1.2.1.2.2.1.2.3".equals(oid.toString())) {
                return new VariableBinding(new OID("1.3.6.1.2.1.2.2.1.2.4"), new OctetString("ether4"));
            }
            //1.3.6.1.2.1.2.2.1.2.4 -> 1.3.6.1.2.1.2.2.1.3.1 = 6
            if ("1.3.6.1.2.1.2.2.1.2.4".equals(oid.toString())) {
                return new VariableBinding(new OID("1.3.6.1.2.1.2.2.1.3.1"), new OctetString("6"));
            }

            return new VariableBinding(
                    SnmpConstants.sysDescr,
                    new OctetString("My Printer - response #" + counter + ", using version: " + version));
        }
    }

    public String getListeningAddress() {
        return listeningAddress;
    }
}
