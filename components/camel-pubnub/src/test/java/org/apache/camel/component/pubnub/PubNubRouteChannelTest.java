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
package org.apache.camel.component.pubnub;

import java.util.concurrent.TimeUnit;

import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PubNubRouteChannelTest extends PubNubTestBase {

    private static final String INBOUND_PUBLISH = "/publish/myPublishKey/mySubscribeKey/0/inbound/0";
    private static final String OUTBOUND_PUBLISH = "/publish/myPublishKey/mySubscribeKey/0/outbound/0";

    @EndpointInject("mock:result")
    private MockEndpoint mockResult;

    @Test
    public void forwardedEventIsPublishedToProducerChannel() throws Exception {
        stubFor(get(urlPathEqualTo("/v2/subscribe/mySubscribeKey/inbound/0"))
                .willReturn(aResponse()
                        .withBody(
                                "{\"t\":{\"t\":\"14607577960932487\",\"r\":1},\"m\":[{\"a\":\"4\",\"f\":0,\"i\":\"sender\",\"p\":{\"t\":\"14607577960925503\",\"r\":1},\"o\":"
                                  + "{\"t\":\"14737141991877032\",\"r\":2},\"k\":\"sub-c-mykey\",\"c\":\"inbound\",\"d\":{\"text\":\"Message\"},\"b\":\"inbound\"}]}")));
        stubFor(get(urlPathEqualTo("/v2/presence/sub-key/mySubscribeKey/channel/inbound/heartbeat"))
                .willReturn(aResponse().withBody("{\"status\": 200, \"message\": \"OK\", \"service\": \"Presence\"}")));
        stubFor(post(urlPathEqualTo(OUTBOUND_PUBLISH))
                .willReturn(aResponse().withStatus(200).withBody("[1,\"Sent\",\"14598111595318003\"]")));
        stubFor(post(urlPathEqualTo(INBOUND_PUBLISH))
                .willReturn(aResponse().withStatus(200).withBody("[1,\"Sent\",\"14598111595318004\"]")));

        mockResult.expectedMinimumMessageCount(1);
        mockResult.expectedHeaderReceived(PubNubConstants.CHANNEL, "inbound");
        context.getRouteController().startRoute("forward");
        MockEndpoint.assertIsSatisfied(context, 10, TimeUnit.SECONDS);
        context.getRouteController().stopRoute("forward");

        assertTrue(findAll(postRequestedFor(urlPathEqualTo(OUTBOUND_PUBLISH))).size() >= 1,
                "The forwarded event should be published to the producer's channel");
        assertTrue(findAll(postRequestedFor(urlPathEqualTo(INBOUND_PUBLISH))).isEmpty(),
                "The forwarded event should not be published to the channel it was received on");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("pubnub:inbound?pubnub=#pubnub").id("forward").autoStartup(false)
                        .to("pubnub:outbound?pubnub=#pubnub")
                        .to("mock:result");
            }
        };
    }
}
