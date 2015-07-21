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

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Ignore;
import org.junit.Test;

/**
 * @version 
 */
public class IrcRouteTest extends CamelTestSupport {
    protected MockEndpoint resultEndpoint;
    protected String body1 = "Message One";
    protected String body2 = "Message Two";
    private boolean sentMessages;    

    @Ignore("test manual, irc.codehaus.org has been closed")   
    @Test
    public void testIrcMessages() throws Exception {
        resultEndpoint = context.getEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedBodiesReceived(body1, body2);

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
                        when(header(IrcConstants.IRC_MESSAGE_TYPE).isEqualTo("PRIVMSG")).to("mock:result").
                        when(header(IrcConstants.IRC_MESSAGE_TYPE).isEqualTo("JOIN")).to("seda:consumerJoined");

                from("seda:consumerJoined").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        sendMessages();
                    }
                });
            }
        };
    }

    protected String sendUri() {
        return "irc://camel-prd-user@irc.codehaus.org:6667/#camel-test?nickname=camel-prd";
    }

    protected String fromUri() {
        return "irc://camel-con-user@irc.codehaus.org:6667/#camel-test?nickname=camel-con";
    }    
    
    /**
     * Lets send messages once the consumer has joined
     */
    protected void sendMessages() {
        if (!sentMessages) {
            sentMessages = true;

            // now the consumer has joined, lets send some messages

            template.sendBody(sendUri(), body1);
            template.sendBody(sendUri(), body2);
        }
    }
}