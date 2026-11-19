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
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.snmp4j.CommunityTarget;
import org.snmp4j.MessageDispatcherImpl;
import org.snmp4j.PDU;
import org.snmp4j.PDUv1;
import org.snmp4j.ScopedPDU;
import org.snmp4j.Snmp;
import org.snmp4j.Target;
import org.snmp4j.UserTarget;
import org.snmp4j.mp.MPv1;
import org.snmp4j.mp.MPv2c;
import org.snmp4j.mp.MPv3;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.security.AuthSHA;
import org.snmp4j.security.PrivAES128;
import org.snmp4j.security.SecurityLevel;
import org.snmp4j.security.SecurityModel;
import org.snmp4j.security.SecurityModels;
import org.snmp4j.security.SecurityProtocols;
import org.snmp4j.security.USM;
import org.snmp4j.security.UsmUser;
import org.snmp4j.smi.Integer32;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.TimeTicks;
import org.snmp4j.smi.UdpAddress;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.transport.DefaultUdpTransportMapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The trap consumer receives only the traps of its snmpVersion and, for SNMPv3, of its user and security level. The
 * traps are sent by a plain SNMP4J sender, and the consumer is the only snmp endpoint of the CamelContext.
 */
public class TrapConsumerVersionTest extends CamelTestSupport {

    private static final String USER = "camel";
    private static final String AUTH_PASSPHRASE = "authpass12";
    private static final String PRIV_PASSPHRASE = "privpass12";
    private static final String V3_AUTH_PRIV = "snmpVersion=3&securityName=" + USER + "&securityLevel=3"
                                               + "&authenticationProtocol=SHA1&authenticationPassphrase=" + AUTH_PASSPHRASE
                                               + "&privacyProtocol=AES128&privacyPassphrase=" + PRIV_PASSPHRASE;

    @RegisterExtension
    AvailablePortFinder.Port trapPort = AvailablePortFinder.find();

    private boolean addedAuthSha;
    private boolean addedPrivAes128;

    @BeforeEach
    void registerSecurityProtocols() {
        // the authentication and privacy protocols of SNMP4J are JVM-wide, and none is registered by default
        SecurityProtocols protocols = SecurityProtocols.getInstance();
        if (protocols.getAuthenticationProtocol(AuthSHA.ID) == null) {
            protocols.addAuthenticationProtocol(new AuthSHA());
            addedAuthSha = true;
        }
        if (protocols.getPrivacyProtocol(PrivAES128.ID) == null) {
            protocols.addPrivacyProtocol(new PrivAES128());
            addedPrivAes128 = true;
        }
    }

    @AfterEach
    void removeSecurityProtocols() {
        if (addedAuthSha) {
            SecurityProtocols.getInstance().removeAuthenticationProtocol(AuthSHA.ID);
        }
        if (addedPrivAes128) {
            SecurityProtocols.getInstance().removePrivacyProtocol(PrivAES128.ID);
        }
    }

    @Test
    public void v3ConsumerReceivesV3AuthPrivTrap() throws Exception {
        MockEndpoint mock = startConsumer(V3_AUTH_PRIV);

        sendV3Trap(USER, AUTH_PASSPHRASE, SecurityLevel.AUTH_PRIV, "accepted");

        assertReceivedOnly(mock, "accepted");
        // the consumer does not register its user in the JVM-wide SecurityModels of SNMP4J
        assertNull(SecurityModels.getInstance().getSecurityModel(new Integer32(SecurityModel.SECURITY_MODEL_USM)));
    }

    @Test
    public void v3ConsumerDropsV2cTrap() throws Exception {
        MockEndpoint mock = startConsumer(V3_AUTH_PRIV);

        sendCommunityTrap(SnmpConstants.version2c, "public", "dropped");
        sendV3Trap(USER, AUTH_PASSPHRASE, SecurityLevel.AUTH_PRIV, "accepted");

        assertReceivedOnly(mock, "accepted");
    }

    @Test
    public void v3ConsumerDropsV1Trap() throws Exception {
        MockEndpoint mock = startConsumer(V3_AUTH_PRIV);

        sendCommunityTrap(SnmpConstants.version1, "public", "dropped");
        sendV3Trap(USER, AUTH_PASSPHRASE, SecurityLevel.AUTH_PRIV, "accepted");

        assertReceivedOnly(mock, "accepted");
    }

    @Test
    public void v3ConsumerDropsTrapBelowSecurityLevel() throws Exception {
        MockEndpoint mock = startConsumer(V3_AUTH_PRIV);

        sendV3Trap(USER, AUTH_PASSPHRASE, SecurityLevel.NOAUTH_NOPRIV, "dropped");
        sendV3Trap(USER, AUTH_PASSPHRASE, SecurityLevel.AUTH_NOPRIV, "dropped");
        sendV3Trap(USER, AUTH_PASSPHRASE, SecurityLevel.AUTH_PRIV, "accepted");

        assertReceivedOnly(mock, "accepted");
    }

    @Test
    public void v3ConsumerDropsTrapOfAnotherUser() throws Exception {
        MockEndpoint mock = startConsumer(V3_AUTH_PRIV);

        sendV3Trap("other", AUTH_PASSPHRASE, SecurityLevel.AUTH_PRIV, "dropped");
        sendV3Trap("other", AUTH_PASSPHRASE, SecurityLevel.NOAUTH_NOPRIV, "dropped");
        sendV3Trap(USER, AUTH_PASSPHRASE, SecurityLevel.AUTH_PRIV, "accepted");

        assertReceivedOnly(mock, "accepted");
    }

    @Test
    public void v3ConsumerDropsTrapWithAnotherPassphrase() throws Exception {
        MockEndpoint mock = startConsumer(V3_AUTH_PRIV);

        sendV3Trap(USER, "otherpass12", SecurityLevel.AUTH_PRIV, "dropped");
        sendV3Trap(USER, AUTH_PASSPHRASE, SecurityLevel.AUTH_PRIV, "accepted");

        assertReceivedOnly(mock, "accepted");
    }

    @Test
    public void v3NoAuthNoPrivConsumerReceivesV3Trap() throws Exception {
        MockEndpoint mock = startConsumer("snmpVersion=3&securityName=" + USER + "&securityLevel=1");

        sendV3Trap(USER, null, SecurityLevel.NOAUTH_NOPRIV, "accepted");

        assertReceivedOnly(mock, "accepted");
    }

    @Test
    public void v2cConsumerReceivesV1AndV2cTrapsAndDropsV3Trap() throws Exception {
        MockEndpoint mock = startConsumer("snmpVersion=1");
        mock.expectedMessageCount(2);

        sendV3Trap(USER, AUTH_PASSPHRASE, SecurityLevel.NOAUTH_NOPRIV, "dropped");
        sendCommunityTrap(SnmpConstants.version1, "public", "first");
        sendCommunityTrap(SnmpConstants.version2c, "public", "second");

        MockEndpoint.assertIsSatisfied(context);
        assertTrue(mock.getReceivedExchanges().get(0).getMessage().getBody(String.class).contains("first"));
        assertTrue(mock.getReceivedExchanges().get(1).getMessage().getBody(String.class).contains("second"));
    }

    @Test
    public void v1ConsumerDropsV3Trap() throws Exception {
        MockEndpoint mock = startConsumer("snmpVersion=0");

        sendV3Trap(USER, AUTH_PASSPHRASE, SecurityLevel.NOAUTH_NOPRIV, "dropped");
        sendCommunityTrap(SnmpConstants.version1, "public", "accepted");

        assertReceivedOnly(mock, "accepted");
    }

    private MockEndpoint startConsumer(String options) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("snmp:127.0.0.1:" + trapPort.getPort() + "?protocol=udp&type=TRAP&" + options).to("mock:trap");
            }
        });
        return getMockEndpoint("mock:trap");
    }

    /**
     * The traps are sent one after the other to the single listener thread of the consumer, so a dropped trap sent
     * before the accepted one would have been received first.
     */
    private void assertReceivedOnly(MockEndpoint mock, String marker) throws Exception {
        mock.expectedMessageCount(1);
        MockEndpoint.assertIsSatisfied(context);
        String body = mock.getReceivedExchanges().get(0).getMessage().getBody(String.class);
        assertTrue(body.contains(marker), "Expected the trap " + marker + " but got: " + body);
        assertEquals(1, mock.getReceivedCounter());
    }

    private void sendCommunityTrap(int version, String community, String marker) throws Exception {
        CommunityTarget<UdpAddress> target = new CommunityTarget<>(trapAddress(), new OctetString(community));
        target.setVersion(version);
        PDU pdu;
        if (version == SnmpConstants.version1) {
            PDUv1 v1 = new PDUv1();
            v1.setType(PDU.V1TRAP);
            v1.setEnterprise(new OID("1.2.3.4"));
            v1.setGenericTrap(PDUv1.ENTERPRISE_SPECIFIC);
            v1.setSpecificTrap(1);
            pdu = v1;
        } else {
            pdu = new PDU();
            pdu.setType(PDU.TRAP);
            addTrapBindings(pdu);
        }
        pdu.add(new VariableBinding(new OID("1.2.3.4.5.1"), new OctetString(marker)));

        MessageDispatcherImpl dispatcher = new MessageDispatcherImpl();
        dispatcher.addMessageProcessingModel(new MPv1());
        dispatcher.addMessageProcessingModel(new MPv2c());
        send(dispatcher, pdu, target);
    }

    private void sendV3Trap(String user, String authPassphrase, int securityLevel, String marker) throws Exception {
        USM usm = new USM(SecurityProtocols.getInstance(), new OctetString(MPv3.createLocalEngineID()), 0);
        if (authPassphrase != null) {
            usm.addUser(new OctetString(user), new UsmUser(
                    new OctetString(user), AuthSHA.ID, new OctetString(authPassphrase), PrivAES128.ID,
                    new OctetString(PRIV_PASSPHRASE)));
        } else {
            usm.addUser(new OctetString(user), new UsmUser(new OctetString(user), null, null, null, null));
        }
        MessageDispatcherImpl dispatcher = new MessageDispatcherImpl();
        dispatcher.addMessageProcessingModel(new MPv3(usm));

        UserTarget<UdpAddress> target = new UserTarget<>();
        target.setAddress(trapAddress());
        target.setVersion(SnmpConstants.version3);
        target.setSecurityName(new OctetString(user));
        target.setSecurityLevel(securityLevel);

        ScopedPDU pdu = new ScopedPDU();
        pdu.setType(PDU.TRAP);
        addTrapBindings(pdu);
        pdu.add(new VariableBinding(new OID("1.2.3.4.5.1"), new OctetString(marker)));
        send(dispatcher, pdu, target);
    }

    private static void addTrapBindings(PDU pdu) {
        pdu.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(5000)));
        pdu.add(new VariableBinding(SnmpConstants.snmpTrapOID, new OID("1.2.3.4.5")));
    }

    private UdpAddress trapAddress() {
        return new UdpAddress("127.0.0.1/" + trapPort.getPort());
    }

    private static void send(MessageDispatcherImpl dispatcher, PDU pdu, Target<UdpAddress> target) throws Exception {
        DefaultUdpTransportMapping transport = new DefaultUdpTransportMapping();
        try (Snmp snmp = new Snmp(dispatcher, transport)) {
            transport.listen();
            snmp.send(pdu, target);
        }
    }
}
