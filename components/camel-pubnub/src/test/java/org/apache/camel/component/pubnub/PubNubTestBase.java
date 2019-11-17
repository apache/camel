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

import java.io.IOException;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.pubnub.api.PNConfiguration;
import com.pubnub.api.PubNub;
import com.pubnub.api.enums.PNLogVerbosity;
import org.apache.camel.BindToRegistry;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static com.pubnub.api.enums.PNHeartbeatNotificationOptions.NONE;

public class PubNubTestBase extends CamelTestSupport {
    private final int port = AvailablePortFinder.getNextAvailable();

    @BindToRegistry("pubnub")
    private PubNub pubnub = createPubNubInstance();

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(options().port(port));


    @Before
    public void beforeEach() throws IOException {
        wireMockRule.start();
    }

    @After
    public void afterEach() {
        pubnub.destroy();
    }

    protected PubNub getPubnub() {
        return pubnub;
    }

    private PubNub createPubNubInstance() {
        PNConfiguration pnConfiguration = new PNConfiguration();

        pnConfiguration.setOrigin("localhost" + ":" + port);
        pnConfiguration.setSecure(false);
        pnConfiguration.setSubscribeKey("mySubscribeKey");
        pnConfiguration.setPublishKey("myPublishKey");
        pnConfiguration.setUuid("myUUID");
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
