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
package org.apache.camel.component.irc;

import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version $Revision$
 */
public class IrcRouteIntegration extends ContextTestSupport {
    protected MockEndpoint resultEndpoint;
    protected String body1 = "Message One";
    protected String body2 = "Message Two";
    private boolean sentMessages;

    public void testIrcMessages() throws Exception {
        resultEndpoint = (MockEndpoint) context.getEndpoint("mock:result");
        resultEndpoint.expectedBodiesReceived(body1, body2);

        resultEndpoint.assertIsSatisfied();
        //Thread.sleep(10000);

        List<Exchange> list = resultEndpoint.getReceivedExchanges();
        for (Exchange exchange : list) {
            log.info("Received exchange: " + exchange + " headers: " + exchange.getIn().getHeaders());
        }
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("irc://camel-con@irc.codehaus.org:6667/%23camel-test").
                        choice().
                        when(header("irc.messageType").isEqualTo("PRIVMSG")).to("mock:result").
                        when(header("irc.messageType").isEqualTo("JOIN")).to("seda:consumerJoined");

                // TODO this causes errors on shutdown...
                //otherwise().to("mock:otherIrcCommands");

                from("seda:consumerJoined").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        sendMessages();
                    }
                });
            }
        };
    }

    /**
     * Lets send messages once the consumer has joined
     */
    protected void sendMessages() {
        if (!sentMessages) {
            sentMessages = true;

            // now the consumer has joined, lets send some messages
            String sendUri = "irc://camel-prd@irc.codehaus.org:6667/%23camel-test";

            template.sendBody(sendUri, body1);
            template.sendBody(sendUri, body2);
        }
    }
}