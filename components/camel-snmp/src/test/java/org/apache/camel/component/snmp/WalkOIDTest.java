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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WalkOIDTest extends SnmpRespondTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(PollOIDTest.class);

    @ParameterizedTest
    @MethodSource("supportedVersions")
    public void testOIDWalk(int version) throws Exception {
        template.sendBody("direct:inV" + version, "");

        MockEndpoint mock = getMockEndpoint("mock:resultV" + version);
        mock.expectedMinimumMessageCount(1);
        mock.assertIsSatisfied();

        String msg = mock.getReceivedExchanges().get(0).getIn().getBody(String.class);
        String pattern = ".*<value>ether1</value>.*<value>ether2</value>.*<value>ether3</value>.*<value>ether4</value>.*";
        Assertions.assertTrue(msg.matches(pattern), "Expected string matching '" + pattern + "'. Got: " + msg);
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {

                from("direct:inV0")
                        .to("snmp:" + getListeningAddress() + "?protocol=udp&type=GET_NEXT&oids=1.3.6.1.2.1.2.2.1.2")
                        .transform(body().convertToString()).to("mock:resultV0");

                from("direct:inV1")
                        .to("snmp:" + getListeningAddress()
                            + "?protocol=udp&snmpVersion=1&type=GET_NEXT&oids=1.3.6.1.2.1.2.2.1.2")
                        .transform(body().convertToString()).to("mock:resultV1");

                from("direct:inV3")
                        .to("snmp:" + getListeningAddress()
                            + "?protocol=udp&snmpVersion=3&type=GET_NEXT&securityName=test&securityLevel=1&oids=1.3.6.1.2.1.2.2.1.2")
                        .transform(body().convertToString()).to("mock:resultV3");
            }
        };
    }
}
