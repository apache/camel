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
import org.junit.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;


public class PubNubSubscriberTest extends PubNubTestBase {

    @EndpointInject(uri = "mock:result")
    private MockEndpoint mockResult;

    @Test
    public void testPubSubMessageSubscribe() throws Exception {
        stubFor(get(urlPathEqualTo("/v2/subscribe/mySubscribeKey/mychannel/0"))
            .willReturn(aResponse()
                .withBody("{\"t\":{\"t\":\"14607577960932487\",\"r\":1},\"m\":[{\"a\":\"4\",\"f\":0,\"i\":\"Publisher-A\",\"p\":{\"t\":\"14607577960925503\",\"r\":1},\"o\":"
                          + "{\"t\":\"14737141991877032\",\"r\":2},\"k\":\"sub-c-4cec9f8e-01fa-11e6-8180-0619f8945a4f\",\"c\":\"mychannel\",\"d\":{\"text\":\"Message\"},\"b\":\"coolChannel\"}]}")));
        stubFor(get(urlPathEqualTo("/v2/presence/sub-key/mySubscribeKey/channel/mychannel/heartbeat"))
            .willReturn(aResponse().withBody("{\"status\": 200, \"message\": \"OK\", \"service\": \"Presence\"}")));

        context.startRoute("subroute");
        mockResult.expectedMessageCount(1);
        mockResult.expectedHeaderReceived(PubNubConstants.CHANNEL, "mychannel");
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("pubnub:mychannel?pubnub=#pubnub").id("subroute").autoStartup(false)
                    .to("mock:result");
            }
        };
    }

}
