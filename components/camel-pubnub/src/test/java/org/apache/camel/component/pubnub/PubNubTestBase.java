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

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.pubnub.api.PNConfiguration;
import com.pubnub.api.PubNub;
import com.pubnub.api.PubNubException;
import com.pubnub.api.UserId;
import com.pubnub.api.enums.PNLogVerbosity;
import org.apache.camel.BindToRegistry;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit5.CamelTestSupport;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static com.pubnub.api.enums.PNHeartbeatNotificationOptions.NONE;

public class PubNubTestBase extends CamelTestSupport {

    private final int port = AvailablePortFinder.getNextAvailable();

    @BindToRegistry("pubnub")
    private PubNub pubnub = createPubNubInstance();

    private WireMockServer wireMockServer = new WireMockServer(options().port(port));

    protected void setupResources() {
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());
    }

    protected void cleanupResources() {
        wireMockServer.stop();
        pubnub.destroy();
    }

    protected PubNub getPubnub() {
        return pubnub;
    }

    private PubNub createPubNubInstance() {
        PNConfiguration pnConfiguration = null;
        try {
            pnConfiguration = new PNConfiguration(new UserId("myUUID"));
        } catch (PubNubException e) {
            throw new RuntimeException(e);
        }

        pnConfiguration.setOrigin("localhost" + ":" + port);
        pnConfiguration.setSecure(false);
        pnConfiguration.setSubscribeKey("mySubscribeKey");
        pnConfiguration.setPublishKey("myPublishKey");
        pnConfiguration.setLogVerbosity(PNLogVerbosity.NONE);
        pnConfiguration.setHeartbeatNotificationOptions(NONE);
        class MockedTimePubNub extends PubNub {

            MockedTimePubNub(PNConfiguration initialConfig) {
                super(initialConfig);
            }

            @Override
            public int getTimestamp() {
                return 1337;
            }

            @Override
            public String getVersion() {
                return "suchJava";
            }

            @Override
            public String getInstanceId() {
                return "PubNubInstanceId";
            }

            @Override
            public String getRequestId() {
                return "PubNubRequestId";
            }

        }

        return new MockedTimePubNub(pnConfiguration);
    }
}
