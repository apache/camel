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

import com.pubnub.api.models.consumer.pubsub.PNPresenceEventResult;

import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import static org.hamcrest.CoreMatchers.equalTo;


public class PubNubPresensTest extends PubNubTestBase {

    @EndpointInject(uri = "mock:result")
    private MockEndpoint mockResult;

    @Test
    public void testPresens() throws Exception {
        stubFor(get(urlPathEqualTo("/v2/presence/sub-key/mySubscribeKey/channel/mychannel/heartbeat"))
            .willReturn(aResponse().withBody("{\"status\": 200, \"message\": \"OK\", \"service\": \"Presence\"}")));

        stubFor(get(urlPathEqualTo("/v2/subscribe/mySubscribeKey/mychannel,mychannel-pnpres/0"))
            .willReturn(aResponse()
                .withBody("{\"t\":{\"t\":\"14637536741734954\",\"r\":1},\"m\":[{\"a\":\"4\",\"f\":512,\"p\":{\"t\":\"14637536740940378\",\"r\":1},\"k\":\"demo-36\",\"c\":\"mychannel-pnpres\","
                          + "\"d\":{\"action\": \"join\", \"timestamp\": 1463753674, \"uuid\": \"24c9bb19-1fcd-4c40-a6f1-522a8a1329ef\", \"occupancy\": 3},\"b\":\"mychannel-pnpres\"},"
                          + "{\"a\":\"4\",\"f\":512,\"p\":{\"t\":\"14637536741726901\",\"r\":1},\"k\":\"demo-36\",\"c\":\"mychannel-pnpres\",\"d\":{\"action\": \"state-change\", "
                          + "\"timestamp\": 1463753674, \"data\": {\"state\": \"cool\"}, \"uuid\": \"24c9bb19-1fcd-4c40-a6f1-522a8a1329ef\", \"occupancy\": 3},\"b\":\"mychannel-pnpres\"}]}")));
        context.startRoute("presence-route");
        mockResult.expectedMessageCount(1);
        mockResult.expectedHeaderReceived(PubNubConstants.CHANNEL, "mychannel");
        assertMockEndpointsSatisfied();
        PNPresenceEventResult presence = mockResult.getReceivedExchanges().get(0).getIn().getBody(PNPresenceEventResult.class);
        assertThat(presence.getEvent(), equalTo("join"));
    }


    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("pubnub://mychannel?pubnub=#pubnub&withPresence=true").id("presence-route")
                    .autoStartup(false)
                    .to("mock:result");
            }
        };
    }

}
