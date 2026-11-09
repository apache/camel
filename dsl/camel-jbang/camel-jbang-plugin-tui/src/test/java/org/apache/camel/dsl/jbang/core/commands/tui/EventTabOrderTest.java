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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The events of a quick exchange share a millisecond: they read newest first like the others.
 */
class EventTabOrderTest {

    @Test
    void eventsOfTheSameMillisecondReadNewestFirst() {
        IntegrationInfo info = new IntegrationInfo();
        // as the integration reports them: oldest first
        info.events.add(event("ExchangeCreated", 1000));
        info.events.add(event("ExchangeSending", 1000));
        info.events.add(event("ExchangeSent", 1000));
        info.events.add(event("ExchangeCompleted", 1000));
        info.events.add(event("ExchangeCreated", 2000));
        info.events.add(event("ExchangeCompleted", 2000));

        EventTab tab = new EventTab(new MonitorContext(new AtomicReference<>(List.of(info)), new AtomicReference<>(List.of())));

        assertThat(tab.sortedEvents(info)).extracting(e -> e.type + "@" + e.timestamp).containsExactly(
                "ExchangeCompleted@2000", "ExchangeCreated@2000",
                "ExchangeCompleted@1000", "ExchangeSent@1000", "ExchangeSending@1000", "ExchangeCreated@1000");
    }

    private static EventInfo event(String type, long timestamp) {
        EventInfo e = new EventInfo();
        e.type = type;
        e.category = "exchange";
        e.timestamp = timestamp;
        return e;
    }
}
