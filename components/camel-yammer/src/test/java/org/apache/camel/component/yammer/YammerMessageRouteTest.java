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
package org.apache.camel.component.yammer;


import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.yammer.model.Messages;
import org.junit.Test;

public class YammerMessageRouteTest extends YammerComponentTestSupport {

    @Test
    public void testConsumeAllMessages() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        assertMockEndpointsSatisfied();
        
        Exchange exchange = mock.getExchanges().get(0);
        Messages messages = exchange.getIn().getBody(Messages.class);

        assertEquals(2, messages.getMessages().size());
        assertEquals("Testing yammer API...", messages.getMessages().get(0).getBody().getPlain());
        assertEquals("(Principal Software Engineer) has #joined the redhat.com network. Take a moment to welcome Jonathan.", messages.getMessages().get(1).getBody().getPlain());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                // using dummy keys here since we are mocking out calls to yammer.com with static json; in a real app, please use your own keys!
                from("yammer:messages?consumerKey=aConsumerKey&consumerSecret=aConsumerSecretKey&accessToken=aAccessToken").to("mock:result");
            }
        };
    }
}
