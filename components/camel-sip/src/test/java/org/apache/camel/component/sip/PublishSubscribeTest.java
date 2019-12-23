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
package org.apache.camel.component.sip;

import javax.sip.message.Request;

import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

@Ignore("Test manually as CI server cannot run this test")
public class PublishSubscribeTest extends CamelTestSupport {

    private int port1;
    private int port2;
    private int port3;

    @EndpointInject("mock:neverland")
    private MockEndpoint unreachableEndpoint;

    @EndpointInject("mock:notification")
    private MockEndpoint resultEndpoint;

    @Produce("direct:start")
    private ProducerTemplate producerTemplate;

    @Override
    @Before
    public void setUp() throws Exception {
        port1 = AvailablePortFinder.getNextAvailable();
        port2 = AvailablePortFinder.getNextAvailable();
        port3 = AvailablePortFinder.getNextAvailable();

        super.setUp();
    }

    @Test
    public void testPresenceAgentBasedPubSub() throws Exception {
        unreachableEndpoint.expectedMessageCount(0);
        resultEndpoint.expectedMinimumMessageCount(1);

        producerTemplate.sendBodyAndHeader(
            "sip://agent@localhost:" + port1 + "?stackName=client&eventHeaderName=evtHdrName&eventId=evtid&fromUser=user2&fromHost=localhost&fromPort=" + port3,
            "EVENT_A",
            "REQUEST_METHOD", Request.PUBLISH);         

        assertMockEndpointsSatisfied();
    }
    
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {  
                // Create PresenceAgent
                fromF("sip://agent@localhost:%s?stackName=PresenceAgent&presenceAgent=true&eventHeaderName=evtHdrName&eventId=evtid", port1)
                    .to("log:neverland")
                    .to("mock:neverland");
                
                fromF("sip://johndoe@localhost:%s?stackName=Subscriber&toUser=agent&toHost=localhost&toPort=%s&eventHeaderName=evtHdrName&eventId=evtid", port2, port1)
                    .to("log:notification")
                    .to("mock:notification");
            }
        };
    }

} 
