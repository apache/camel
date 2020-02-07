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
package org.apache.camel.component.hipchat;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.http.StatusLine;
import org.junit.Test;

public class HipchatComponentProducerTest extends CamelTestSupport {
    @EndpointInject("direct:start")
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    private PostCallback callback = new PostCallback();

    @Test
    public void sendInOnly() throws Exception {
        result.expectedMessageCount(1);

        Exchange exchange = template.send("direct:start", ExchangePattern.InOnly, new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(HipchatConstants.TO_ROOM, "CamelUnitTest");
                exchange.getIn().setHeader(HipchatConstants.TO_USER, "CamelUnitTestUser");
                exchange.getIn().setBody("This is my unit test message.");
            }
        });

        assertMockEndpointsSatisfied();

        assertCommonResultExchange(result.getExchanges().get(0));
        assertNullExchangeHeader(result.getExchanges().get(0));

        assertResponseMessage(exchange.getIn());
    }

    @Test
    public void sendInOut() throws Exception {
        result.expectedMessageCount(1);

        Exchange exchange = template.send("direct:start", ExchangePattern.InOut, new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(HipchatConstants.TO_ROOM, "CamelUnitTest");
                exchange.getIn().setHeader(HipchatConstants.TO_USER, "CamelUnitTestUser");
                exchange.getIn().setHeader(HipchatConstants.MESSAGE_BACKGROUND_COLOR, "CamelUnitTestBkColor");
                exchange.getIn().setHeader(HipchatConstants.MESSAGE_FORMAT, "CamelUnitTestFormat");
                exchange.getIn().setHeader(HipchatConstants.TRIGGER_NOTIFY, "CamelUnitTestNotify");
                exchange.getIn().setBody("This is my unit test message.");
            }
        });

        assertMockEndpointsSatisfied();

        assertCommonResultExchange(result.getExchanges().get(0));
        assertRemainingResultExchange(result.getExchanges().get(0));

        assertResponseMessage(exchange.getIn());

    }

    @Test
    public void sendInOutRoomOnly() throws Exception {
        result.expectedMessageCount(1);

        Exchange exchange = template.send("direct:start", ExchangePattern.InOut, new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(HipchatConstants.TO_ROOM, "CamelUnitTest");
                exchange.getIn().setHeader(HipchatConstants.MESSAGE_BACKGROUND_COLOR, "CamelUnitTestBkColor");
                exchange.getIn().setHeader(HipchatConstants.MESSAGE_FORMAT, "CamelUnitTestFormat");
                exchange.getIn().setHeader(HipchatConstants.TRIGGER_NOTIFY, "CamelUnitTestNotify");
                exchange.getIn().setBody("This is my unit test message.");
            }
        });

        assertMockEndpointsSatisfied();
        Exchange resultExchange = result.getExchanges().get(0);
        assertIsInstanceOf(String.class, resultExchange.getIn().getBody());
        assertEquals("This is my unit test message.", resultExchange.getIn().getBody(String.class));
        assertEquals("CamelUnitTest", resultExchange.getIn().getHeader(HipchatConstants.TO_ROOM));
        assertNull(resultExchange.getIn().getHeader(HipchatConstants.TO_USER));
        assertNull(resultExchange.getIn().getHeader(HipchatConstants.TO_USER_RESPONSE_STATUS));
        assertNotNull(resultExchange.getIn().getHeader(HipchatConstants.TO_ROOM_RESPONSE_STATUS));

        assertRemainingResultExchange(result.getExchanges().get(0));

        assertEquals(204, exchange.getIn().getHeader(HipchatConstants.TO_ROOM_RESPONSE_STATUS, StatusLine.class).getStatusCode());
        assertNotNull(callback);
        assertNotNull(callback.called);
        assertEquals("This is my unit test message.", callback.called.get(HipchatApiConstants.API_MESSAGE));
        assertEquals("CamelUnitTestBkColor", callback.called.get(HipchatApiConstants.API_MESSAGE_COLOR));
        assertEquals("CamelUnitTestFormat", callback.called.get(HipchatApiConstants.API_MESSAGE_FORMAT));
        assertEquals("CamelUnitTestNotify", callback.called.get(HipchatApiConstants.API_MESSAGE_NOTIFY));
    }

    @Test
    public void sendInOutUserOnly() throws Exception {
        result.expectedMessageCount(1);

        Exchange exchange = template.send("direct:start", ExchangePattern.InOut, new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(HipchatConstants.TO_USER, "CamelUnitTest");
                exchange.getIn().setHeader(HipchatConstants.MESSAGE_BACKGROUND_COLOR, "CamelUnitTestBkColor");
                exchange.getIn().setHeader(HipchatConstants.MESSAGE_FORMAT, "CamelUnitTestFormat");
                exchange.getIn().setHeader(HipchatConstants.TRIGGER_NOTIFY, "CamelUnitTestNotify");
                exchange.getIn().setBody("This is my unit test message.");
            }
        });

        assertMockEndpointsSatisfied();
        Exchange resultExchange = result.getExchanges().get(0);
        assertIsInstanceOf(String.class, resultExchange.getIn().getBody());
        assertEquals("This is my unit test message.", resultExchange.getIn().getBody(String.class));
        assertEquals("CamelUnitTest", resultExchange.getIn().getHeader(HipchatConstants.TO_USER));
        assertNull(resultExchange.getIn().getHeader(HipchatConstants.TO_ROOM));
        assertNull(resultExchange.getIn().getHeader(HipchatConstants.TO_ROOM_RESPONSE_STATUS));
        assertNotNull(resultExchange.getIn().getHeader(HipchatConstants.TO_USER_RESPONSE_STATUS));

        assertRemainingResultExchange(result.getExchanges().get(0));

        assertEquals(204, exchange.getIn().getHeader(HipchatConstants.TO_USER_RESPONSE_STATUS, StatusLine.class).getStatusCode());
        assertNotNull(callback);
        assertNotNull(callback.called);
        assertEquals("This is my unit test message.", callback.called.get(HipchatApiConstants.API_MESSAGE));
        assertNull(callback.called.get(HipchatApiConstants.API_MESSAGE_COLOR));
        assertEquals("CamelUnitTestFormat", callback.called.get(HipchatApiConstants.API_MESSAGE_FORMAT));
        assertEquals("CamelUnitTestNotify", callback.called.get(HipchatApiConstants.API_MESSAGE_NOTIFY));
    }

    private void assertNullExchangeHeader(Exchange resultExchange) {
        assertNull(resultExchange.getIn().getHeader(HipchatConstants.FROM_USER));
        assertNull(resultExchange.getIn().getHeader(HipchatConstants.MESSAGE_BACKGROUND_COLOR));
        assertNull(resultExchange.getIn().getHeader(HipchatConstants.MESSAGE_FORMAT));
        assertNull(resultExchange.getIn().getHeader(HipchatConstants.TRIGGER_NOTIFY));
    }

    private void assertRemainingResultExchange(Exchange resultExchange) {
        assertEquals("CamelUnitTestBkColor", resultExchange.getIn().getHeader(HipchatConstants.MESSAGE_BACKGROUND_COLOR));
        assertEquals("CamelUnitTestFormat", resultExchange.getIn().getHeader(HipchatConstants.MESSAGE_FORMAT));
        assertEquals("CamelUnitTestNotify", resultExchange.getIn().getHeader(HipchatConstants.TRIGGER_NOTIFY));
    }

    private void assertResponseMessage(Message message) {
        assertEquals(204, message.getHeader(HipchatConstants.TO_ROOM_RESPONSE_STATUS, StatusLine.class).getStatusCode());
        assertEquals(204, message.getHeader(HipchatConstants.TO_USER_RESPONSE_STATUS, StatusLine.class).getStatusCode());
    }

    private void assertCommonResultExchange(Exchange resultExchange) {
        assertIsInstanceOf(String.class, resultExchange.getIn().getBody());
        assertEquals("This is my unit test message.", resultExchange.getIn().getBody(String.class));
        assertEquals("CamelUnitTest", resultExchange.getIn().getHeader(HipchatConstants.TO_ROOM));
        assertEquals("CamelUnitTestUser", resultExchange.getIn().getHeader(HipchatConstants.TO_USER));
        assertNotNull(resultExchange.getIn().getHeader(HipchatConstants.TO_USER_RESPONSE_STATUS));
        assertNotNull(resultExchange.getIn().getHeader(HipchatConstants.TO_ROOM_RESPONSE_STATUS));
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        final CamelContext context = super.createCamelContext();
        HipchatComponent component = new HipchatTestComponent(context, callback);
        component.init();
        context.addComponent("hipchat", component);
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("hipchat:http:api.hipchat.com?authToken=anything")
                        .to("mock:result");
            }
        };
    }

    public static class PostCallback {
        public Map<String, String> called;
        public void call(Map<String, String> postParam) {
            this.called = postParam;
        }
    }


}
