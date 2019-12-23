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
package org.apache.camel.component.irc.it;

import java.util.List;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.irc.IrcConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class IrcMultiChannelRouteTest extends IrcIntegrationTestSupport {
    protected String body1 = "Message One";
    protected String body2 = "Message Two";
    protected String body3 = "Message Three";

    @EndpointInject("mock:joined")
    private MockEndpoint joined;


    @Test
    public void testIrcMessages() throws Exception {
        resetMock(joined);
        joined.expectedMessageCount(2);
        joined.expectedHeaderValuesReceivedInAnyOrder(IrcConstants.IRC_TARGET, properties.get("channel1"), properties.get("channel2"));
        joined.assertIsSatisfied();

        sendMessages();

        //consumer is going to receive two copies of body3
        resultEndpoint.expectedBodiesReceivedInAnyOrder(body1, body2, body3, body3);

        resultEndpoint.assertIsSatisfied();

        List<Exchange> list = resultEndpoint.getReceivedExchanges();
        for (Exchange exchange : list) {
            log.info("Received exchange: " + exchange + " headers: " + exchange.getIn().getHeaders());
        }
    }   
    
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(fromUri()).
                        choice().
                        when(header(IrcConstants.IRC_MESSAGE_TYPE).isEqualTo("PRIVMSG")).to("direct:mock").
                        when(header(IrcConstants.IRC_MESSAGE_TYPE).isEqualTo("JOIN")).to(joined);

                from("direct:mock").filter(e -> !e.getIn().getBody(String.class).contains("VERSION")).to(resultEndpoint);
            }
        };
    }


    /**
     * Lets send messages once the consumer has joined
     */
    protected void sendMessages() {
        template.sendBodyAndHeader(sendUri(), body1, "irc.sendTo", properties.get("channel1"));
        template.sendBodyAndHeader(sendUri(), body2, "irc.sendTo", properties.get("channel2"));
        template.sendBody(sendUri(), body3);
    }

    @Override
    protected String sendUri() {
        return "ircs://camel-prd@{{server}}?nickname=camel-prd&channels={{channel1}},{{channel2}}";
    }

    @Override
    protected String fromUri() {
        return "ircs://camel-con@{{server}}??nickname=camel-con&channels={{channel1}},{{channel2}}";
    }
}
