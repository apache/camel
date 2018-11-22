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
package org.apache.camel.component.irc.it;

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.irc.IrcConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Ignore;
import org.junit.Test;

/**
 * @version 
 */
public class IrcPrivmsgTest extends IrcIntegrationTestSupport {
    protected String expectedBody1 = "Message One";
    protected String expectedBody2 = "Message Two";

    protected String body1 = expectedBody1;
    protected String body2 = expectedBody2;

    private boolean sentMessages;    

    @Test
    public void testIrcPrivateMessages() throws Exception {
        resultEndpoint.expectedBodiesReceived(expectedBody1, expectedBody2);

        resultEndpoint.assertIsSatisfied();

        List<Exchange> list = resultEndpoint.getReceivedExchanges();
        for (Exchange exchange : list) {
            log.info("Received exchange: " + exchange + " headers: " + exchange.getIn().getHeaders());
        }
    }   
    
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(fromUri()).
                        choice().
                        when(header(IrcConstants.IRC_MESSAGE_TYPE).isEqualTo("PRIVMSG")).to("direct:mock").
                        when(header(IrcConstants.IRC_MESSAGE_TYPE).isEqualTo("JOIN")).to("seda:consumerJoined");

                from("seda:consumerJoined")
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            sendMessages();
                        }
                    });

                from("direct:mock").filter(e -> !e.getIn().getBody(String.class).contains("VERSION")).to(resultEndpoint);
            }
        };
    }

    @Override
    protected String sendUri() {
        return "ircs://{{camelTo}}@{{server}}?channels={{channel1}}&username={{username}}&password={{password}}";
    }

    /**
     * Lets send messages once the consumer has joined
     */
    protected void sendMessages() throws InterruptedException {
        if (!sentMessages) {
            sentMessages = true;

            template.sendBodyAndHeader(sendUri(), body1, "irc.target", properties.get("camelFrom"));
            template.sendBodyAndHeader(sendUri(), body2, "irc.target", properties.get("camelFrom"));
        }
    }
}
