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
package org.apache.camel.component.pubnub;

import com.pubnub.api.Callback;

import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.json.JSONObject;
import org.junit.Test;

public class PubNubPresensTest extends CamelTestSupport {
    boolean connected;
    private PubNubMock pubnubMock = new PubNubMock("foo", "bar");

    @EndpointInject(uri = "mock:result")
    private MockEndpoint mockResult;

    @Test
    public void testPresens() throws Exception {
        mockResult.expectedMessageCount(1);
        mockResult.expectedHeaderReceived(PubNubConstants.CHANNEL, "mychannel");
        pubnubMock.subscribe("mychannel", new Callback() {
            @Override
            public void connectCallback(String channel, Object message) {
                connected = true;
            }
        });
        assertMockEndpointsSatisfied();
        assertTrue(connected);
        JSONObject presenceResponse = mockResult.getReceivedExchanges().get(0).getIn().getBody(JSONObject.class);
        assertEquals("join", presenceResponse.getString("action"));
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();
        registry.bind("pubnub", new PubNubMock("dummy", "dummy"));
        return registry;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                //@formatter:off
                from("pubnub://presence:mychannel?pubnub=#pubnub")
                    .to("log:org.apache.camel.component.pubnub?showAll=true&multiline=true")
                    .to("mock:result");
                //@formatter:on
            }
        };
    }

}
