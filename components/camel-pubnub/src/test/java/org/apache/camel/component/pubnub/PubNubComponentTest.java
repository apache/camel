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

import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.json.JSONObject;
import org.junit.Test;

public class PubNubComponentTest extends CamelTestSupport {
    private String endpoint = "pubnub:pubsub:someChannel?pubnub=#pubnub";

    @EndpointInject(uri = "mock:result")
    private MockEndpoint mockResult;

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();
        registry.bind("pubnub", new PubNubMock("dummy", "dummy"));
        return registry;
    }

    @Test
    public void testPubNub() throws Exception {
        mockResult.expectedMessageCount(1);
        mockResult.expectedHeaderReceived("CamelPubNubChannel", "someChannel");
        mockResult.expectedBodiesReceived("{\"hi\":\"there\"}");
        JSONObject jo = new JSONObject();
        jo.put("hi", "there");
        template.sendBody("direct:publish", jo);
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from(endpoint).to("mock:result");
                from("direct:publish").to(endpoint);
            }
        };
    }

}
