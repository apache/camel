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
package org.apache.camel.component.aws2.eventbridge;

import org.apache.camel.component.aws2.eventbridge.client.EventbridgeClientFactory;
import org.apache.camel.component.aws2.eventbridge.client.EventbridgeInternalClient;
import org.apache.camel.component.aws2.eventbridge.client.impl.EventbridgeClientIAMOptimizedImpl;
import org.apache.camel.component.aws2.eventbridge.client.impl.EventbridgeClientSessionTokenImpl;
import org.apache.camel.component.aws2.eventbridge.client.impl.EventbridgeClientStandardImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class EventbridgeClientFactoryTest {

    @Test
    public void getStandardEventbridgeClientDefault() {
        EventbridgeConfiguration eventbridgeConfiguration = new EventbridgeConfiguration();
        EventbridgeInternalClient eventbridgeClient = EventbridgeClientFactory.getEventbridgeClient(eventbridgeConfiguration);
        assertTrue(eventbridgeClient instanceof EventbridgeClientStandardImpl);
    }

    @Test
    public void getStandardEventbridgeClient() {
        EventbridgeConfiguration eventbridgeConfiguration = new EventbridgeConfiguration();
        eventbridgeConfiguration.setUseDefaultCredentialsProvider(false);
        EventbridgeInternalClient eventbridgeClient = EventbridgeClientFactory.getEventbridgeClient(eventbridgeConfiguration);
        assertTrue(eventbridgeClient instanceof EventbridgeClientStandardImpl);
    }

    @Test
    public void getIAMOptimizedEventbridgeClient() {
        EventbridgeConfiguration eventbridgeConfiguration = new EventbridgeConfiguration();
        eventbridgeConfiguration.setUseDefaultCredentialsProvider(true);
        EventbridgeInternalClient eventbridgeClient = EventbridgeClientFactory.getEventbridgeClient(eventbridgeConfiguration);
        assertTrue(eventbridgeClient instanceof EventbridgeClientIAMOptimizedImpl);
    }

    @Test
    public void getSessionTokenEventbridgeClient() {
        EventbridgeConfiguration eventbridgeConfiguration = new EventbridgeConfiguration();
        eventbridgeConfiguration.setUseSessionCredentials(true);
        EventbridgeInternalClient eventbridgeClient = EventbridgeClientFactory.getEventbridgeClient(eventbridgeConfiguration);
        assertTrue(eventbridgeClient instanceof EventbridgeClientSessionTokenImpl);
    }
}
