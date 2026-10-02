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

import java.util.concurrent.atomic.AtomicInteger;

import com.google.gson.JsonPrimitive;
import com.pubnub.api.UserId;
import com.pubnub.api.enums.PNLogVerbosity;
import com.pubnub.api.java.v2.PNConfiguration;
import com.pubnub.api.models.consumer.pubsub.BasePubSubResult;
import com.pubnub.api.models.consumer.pubsub.PNMessageResult;
import com.pubnub.api.models.consumer.pubsub.PNPresenceEventResult;
import com.pubnub.internal.java.PubNubForJavaImpl;
import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.pubnub.api.enums.PNHeartbeatNotificationOptions.NONE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The consumers of several endpoints share one PubNub client: each consumer only gets the messages of its own channel,
 * only once after a restart, none after it stopped, and removing one route does not destroy the shared client.
 * <p>
 * The PubNub client hands every message it receives to all its listeners, so the tests announce messages through the
 * listeners of the real client instead of going through a subscribe response.
 */
public class PubNubConsumerListenerTest extends PubNubTestBase {

    @BindToRegistry("sharedPubnub")
    private final SharedPubNub sharedPubnub = new SharedPubNub(port.getPort());

    @BeforeEach
    public void stubSubscribe() {
        stubFor(get(urlPathMatching("/v2/subscribe/mySubscribeKey/.*"))
                .willReturn(aResponse().withBody("{\"t\":{\"t\":\"14607577960932487\",\"r\":1},\"m\":[]}")
                        .withFixedDelay(100)));
        stubFor(get(urlPathMatching("/v2/presence/.*"))
                .willReturn(aResponse().withBody("{\"status\": 200, \"message\": \"OK\", \"service\": \"Presence\"}")));
    }

    @Override
    protected void cleanupResources() {
        super.cleanupResources();
        sharedPubnub.destroy();
    }

    @Test
    public void testConsumerOnlyReceivesItsOwnChannel() throws Exception {
        context.getRouteController().startRoute("alpha");
        context.getRouteController().startRoute("beta");

        MockEndpoint alpha = getMockEndpoint("mock:alpha");
        alpha.expectedMessageCount(1);
        alpha.expectedHeaderReceived(PubNubConstants.CHANNEL, "alpha");
        MockEndpoint beta = getMockEndpoint("mock:beta");
        beta.expectedMessageCount(0);

        sharedPubnub.announce("alpha");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testWildcardSubscriptionAndPresenceEvents() throws Exception {
        context.getRouteController().startRoute("alpha");
        context.getRouteController().startRoute("news");

        MockEndpoint alpha = getMockEndpoint("mock:alpha");
        alpha.expectedMessageCount(1);
        alpha.message(0).body().isInstanceOf(PNPresenceEventResult.class);
        MockEndpoint news = getMockEndpoint("mock:news");
        news.expectedMessageCount(1);
        news.expectedHeaderReceived(PubNubConstants.CHANNEL, "news.sports");

        // a message of a channel matched by the wildcard subscription, and a presence event (the client removes the
        // -pnpres suffix of the presence channel)
        sharedPubnub.announce("news.sports", "news.*");
        sharedPubnub.announcePresence("alpha");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testCommaSeparatedChannels() throws Exception {
        context.getRouteController().startRoute("multi");

        MockEndpoint multi = getMockEndpoint("mock:multi");
        multi.expectedMessageCount(2);

        // the PubNub client subscribes to each channel of a comma separated list
        sharedPubnub.announce("delta");
        sharedPubnub.announce("epsilon");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testMessageReceivedOnceAfterRestart() throws Exception {
        context.getRouteController().startRoute("alpha");
        context.getRouteController().stopRoute("alpha");
        context.getRouteController().startRoute("alpha");

        MockEndpoint alpha = getMockEndpoint("mock:alpha");
        alpha.expectedMessageCount(1);

        sharedPubnub.announce("alpha");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testNoMessageAfterStop() throws Exception {
        context.getRouteController().startRoute("alpha");
        context.getRouteController().stopRoute("alpha");

        MockEndpoint alpha = getMockEndpoint("mock:alpha");
        alpha.expectedMessageCount(0);

        sharedPubnub.announce("alpha");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testRemovingRouteKeepsSharedClient() throws Exception {
        context.getRouteController().startRoute("alpha");
        context.getRouteController().startRoute("beta");

        // alpha is the only route that uses its endpoint, so removing the route stops and removes the endpoint
        context.getRouteController().stopRoute("alpha");
        context.removeRoute("alpha");

        assertEquals(0, sharedPubnub.destroyed.get(), "The shared PubNub client must not be destroyed");
        assertSame(sharedPubnub, context.getEndpoint("pubnub:beta?pubnub=#sharedPubnub", PubNubEndpoint.class).getPubnub());

        MockEndpoint beta = getMockEndpoint("mock:beta");
        beta.expectedMessageCount(1);

        sharedPubnub.announce("beta");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testEndpointDestroysItsOwnClient() throws Exception {
        PubNubEndpoint endpoint = context.getEndpoint(
                "pubnub:gamma?subscribeKey=mySubscribeKey&publishKey=myPublishKey&secretKey=mySecretKey&authKey=myAuthKey"
                                                      + "&uuid=myUUID",
                PubNubEndpoint.class);
        assertNotNull(endpoint.getPubnub());

        endpoint.stop();
        assertNull(endpoint.getPubnub());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("pubnub:alpha?pubnub=#sharedPubnub").id("alpha").autoStartup(false)
                        .to("mock:alpha");

                from("pubnub:beta?pubnub=#sharedPubnub").id("beta").autoStartup(false)
                        .to("mock:beta");

                from("pubnub:news.*?pubnub=#sharedPubnub").id("news").autoStartup(false)
                        .to("mock:news");

                from("pubnub:delta,epsilon?pubnub=#sharedPubnub").id("multi").autoStartup(false)
                        .to("mock:multi");
            }
        };
    }

    /**
     * A real PubNub client that counts how often it is destroyed and lets the test hand a message to its listeners.
     */
    static class SharedPubNub extends PubNubForJavaImpl {

        final AtomicInteger destroyed = new AtomicInteger();

        SharedPubNub(int port) {
            super(createConfiguration(port));
        }

        private static PNConfiguration createConfiguration(int port) {
            try {
                return PNConfiguration.builder(new UserId("myUUID"), "mySubscribeKey")
                        .publishKey("myPublishKey")
                        .secure(false)
                        .origin("localhost:" + port)
                        .logVerbosity(PNLogVerbosity.NONE)
                        .heartbeatNotificationOptions(NONE)
                        .build();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        void announce(String channel) {
            // the client gives no subscription when the message came through the channel itself
            announce(channel, null);
        }

        void announce(String channel, String subscription) {
            getListenerManager().announce(new PNMessageResult(
                    new BasePubSubResult(channel, subscription, 14607577960932488L, null, "publisher"),
                    new JsonPrimitive("Hello " + channel), null, null));
        }

        void announcePresence(String channel) {
            getListenerManager().announce(new PNPresenceEventResult(
                    "join", "someone", 1460757796L, 1, null, channel, null, 14607577960932488L, null, null, null, false,
                    null));
        }

        @Override
        public void destroy() {
            destroyed.incrementAndGet();
            super.destroy();
        }
    }
}
