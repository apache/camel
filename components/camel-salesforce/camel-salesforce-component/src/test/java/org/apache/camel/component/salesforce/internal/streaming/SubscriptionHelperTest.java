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
package org.apache.camel.component.salesforce.internal.streaming;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.camel.component.salesforce.SalesforceComponent;
import org.apache.camel.component.salesforce.SalesforceEndpointConfig;
import org.apache.camel.component.salesforce.SalesforceHttpClient;
import org.apache.camel.component.salesforce.internal.SalesforceSession;
import org.cometd.bayeux.Channel;
import org.cometd.bayeux.Message;
import org.cometd.bayeux.client.ClientSession;
import org.cometd.bayeux.client.ClientSession.Extension;
import org.cometd.client.BayeuxClient;
import org.cometd.common.HashMapMessage;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SubscriptionHelperTest {

    static final ClientSession NOT_USED = null;

    final SalesforceComponent component = mock(SalesforceComponent.class);

    final SalesforceSession session = mock(SalesforceSession.class);

    final SalesforceEndpointConfig config = mock(SalesforceEndpointConfig.class);

    @Before
    public void setupMocks() {
        when(component.getSession()).thenReturn(session);

        when(session.getInstanceUrl()).thenReturn("https://some.url");

        when(component.getConfig()).thenReturn(config);
        when(component.getConfig().getApiVersion()).thenReturn(SalesforceEndpointConfig.DEFAULT_VERSION);

        when(config.getHttpClient()).thenReturn(mock(SalesforceHttpClient.class));

    }

    @Test
    public void shouldSupportInitialConfigMapWithTwoKeySyntaxes() throws Exception {
        final Map<String, Integer> initialReplayIdMap = new HashMap<>();
        initialReplayIdMap.put("my-topic-1", 10);
        initialReplayIdMap.put("/topic/my-topic-1", 20);
        initialReplayIdMap.put("/topic/my-topic-2", 30);

        when(config.getDefaultReplayId()).thenReturn(14);

        when(config.getInitialReplayIdMap()).thenReturn(initialReplayIdMap);

        assertEquals("Expecting replayId for `my-topic-1` to be 10, as short topic names have priority", (Object) 10,
                fetchReplayExtensionValue("my-topic-1").get("/topic/my-topic-1"));

        assertEquals("Expecting replayId for `my-topic-2` to be 30, the only one given", (Object) 30,
                fetchReplayExtensionValue("my-topic-2").get("/topic/my-topic-2"));

        assertEquals("Expecting replayId for `my-topic-3` to be 14, the default", (Object) 14,
                fetchReplayExtensionValue("my-topic-3").get("/topic/my-topic-3"));
    }

    Map<String, Integer> fetchReplayExtensionValue(final String topicName) throws Exception {
        final BayeuxClient client = SubscriptionHelper.createClient(component, topicName);

        final List<Extension> extensions = client.getExtensions();

        final Optional<Extension> extension = extensions.stream().filter(e -> e instanceof CometDReplayExtension)
                .findFirst();

        assertTrue("Client should be configured with CometDReplayExtension extension", extension.isPresent());

        final CometDReplayExtension cometDReplayExtension = (CometDReplayExtension) extension.get();

        final Message.Mutable handshake = new HashMapMessage();
        handshake.setChannel(Channel.META_HANDSHAKE);
        handshake.put(Message.EXT_FIELD, Collections.singletonMap("replay", true));

        cometDReplayExtension.rcvMeta(NOT_USED, handshake);

        final Message.Mutable subscription = new HashMapMessage();
        subscription.setChannel(Channel.META_SUBSCRIBE);
        cometDReplayExtension.sendMeta(NOT_USED, subscription);

        @SuppressWarnings("unchecked")
        final Map<String, Integer> replays = (Map<String, Integer>) subscription.getExt().get("replay");

        return replays;
    }
}
