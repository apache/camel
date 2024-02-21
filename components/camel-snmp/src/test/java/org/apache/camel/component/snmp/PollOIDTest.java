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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.snmp4j.mp.SnmpConstants;

public class PollOIDTest extends SnmpRespondTestSupport {

    @ParameterizedTest
    @MethodSource("supportedVersions")
    public void testOIDPolling(int version) throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:resultV" + version);
        mock.expectedMinimumMessageCount(1);
        mock.assertIsSatisfied();

        SnmpMessage snmpMessage = mock.getReceivedExchanges().get(0).getIn().getBody(SnmpMessage.class);
        String responseToMatch = "My Printer - response #\\d+, using version: " + version;
        String receivedMessage = snmpMessage.getSnmpMessage().getVariable(SnmpConstants.sysDescr).toString();
        Assertions.assertTrue(receivedMessage.matches(responseToMatch),
                "Expected string matching '" + responseToMatch + "'. Got: " + receivedMessage);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(String.format("snmp:%s?protocol=udp&type=POLL&snmpVersion=0&oids=%s&delay=100", getListeningAddress(),
                        SnmpConstants.sysName))
                        .to("mock:resultV0");

                from(String.format("snmp:%s?protocol=udp&type=POLL&snmpVersion=1&oids=%s&delay=100", getListeningAddress(),
                        SnmpConstants.sysName))
                        .to("mock:resultV1");

                from(String.format(
                        "snmp:%s?protocol=udp&type=POLL&snmpVersion=3&securityName=%s&securityLevel=1&oids=%s&delay=100",
                        getListeningAddress(), SECURITY_NAME, SnmpConstants.sysName))
                        .to("mock:resultV3");
            }
        };
    }
}
