/**
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
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Test;

public class PublishSubscribeTest extends CamelTestSupport {
    private static final transient Log LOG = LogFactory.getLog(PublishSubscribeTest.class);

    @EndpointInject(uri = "mock:neverland")
    protected MockEndpoint unreachableEndpoint;

    @EndpointInject(uri = "mock:notification")
    protected MockEndpoint resultEndpoint;

    @Produce(uri = "direct:start")
    protected ProducerTemplate producerTemplate;
    
    @Test
    public void testPresenceAgentBasedPubSub() throws Exception {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Beginning Test ---> testStatefulTransactionalTCPRequestReply()");
        }

        unreachableEndpoint.expectedMessageCount(0);
        // we get a header and a body hence 2 messages
        resultEndpoint.expectedMessageCount(2);
        
        producerTemplate.sendBodyAndHeader(
            "sip://agent@localhost:5152?stackName=client&eventHeaderName=evtHdrName&eventId=evtid&fromUser=user2&fromHost=localhost&fromPort=3534", 
            "EVENT_A",
            "REQUEST_METHOD", Request.PUBLISH);         

        assertMockEndpointsSatisfied();
            
        if (LOG.isDebugEnabled()) {
            LOG.debug("Completed Test ---> testStatefulTransactionalTCPRequestReply()");
        }        
    }
    
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {  
                // Create PresenceAgent
                from("sip://agent@localhost:5152?stackName=PresenceAgent&presenceAgent=true&eventHeaderName=evtHdrName&eventId=evtid")
                    .to("mock:neverland");
                
                from("sip://johndoe@localhost:5154?stackName=Subscriber&toUser=agent&toHost=localhost&toPort=5152&eventHeaderName=evtHdrName&eventId=evtid")
                    .to("log:ReceivedEvent")
                    .to("mock:notification");
            }
        };
    }

} 
