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
package org.apache.camel.component.ai.tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiToolRegistryListenerTest {

    private AiToolRegistry registry;
    private RecordingListener listener;

    @BeforeEach
    void setUp() {
        registry = new AiToolRegistry();
        listener = new RecordingListener();
        registry.addListener(listener);
    }

    @Test
    void testRegisteredEventOnPut() {
        AiToolSpec spec = spec("getWeather");
        registry.put("weather", spec);

        assertThat(listener.events).containsExactly(new Event("registered", "weather", spec));
    }

    @Test
    void testNoDuplicateEventOnRepeatedPutOfSameSpec() {
        AiToolSpec spec = spec("getWeather");
        registry.put("weather", spec);
        registry.put("weather", spec);

        assertThat(listener.events)
                .as("Re-adding the same spec instance should not fire a second event")
                .hasSize(1);
    }

    @Test
    void testDeregisteredEventOnRemove() {
        AiToolSpec spec = spec("getWeather");
        registry.put("weather", spec);
        registry.remove("weather", spec);

        assertThat(listener.events).containsExactly(
                new Event("registered", "weather", spec),
                new Event("deregistered", "weather", spec));
    }

    @Test
    void testNoEventOnRemovingAbsentSpec() {
        registry.remove("weather", spec("getWeather"));
        registry.removeDefault(spec("getWeather"));

        assertThat(listener.events)
                .as("Removing a spec that was never registered should not fire events")
                .isEmpty();
    }

    @Test
    void testDefaultPoolEventsUseNullTag() {
        AiToolSpec spec = spec("getWeather");
        registry.putDefault(spec);
        registry.removeDefault(spec);

        assertThat(listener.events).containsExactly(
                new Event("registered", null, spec),
                new Event("deregistered", null, spec));
    }

    @Test
    void testNoEventWhenPutThrowsOnDuplicateName() {
        registry.put("weather", spec("getWeather"));
        listener.events.clear();

        assertThatThrownBy(() -> registry.put("weather", spec("getWeather")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(listener.events)
                .as("A rejected registration should not fire an event")
                .isEmpty();
    }

    @Test
    void testListenerExceptionDoesNotBreakRegistrationOrOtherListeners() {
        registry.addListener(new AiToolRegistryListener() {
            @Override
            public void toolRegistered(String tag, AiToolSpec spec) {
                throw new IllegalStateException("boom");
            }

            @Override
            public void toolDeregistered(String tag, AiToolSpec spec) {
                throw new IllegalStateException("boom");
            }
        });
        RecordingListener second = new RecordingListener();
        registry.addListener(second);

        AiToolSpec spec = spec("getWeather");
        registry.put("weather", spec);

        assertThat(registry.getToolsByTag("weather"))
                .as("Registration should succeed despite a failing listener")
                .contains(spec);
        assertThat(second.events)
                .as("Listeners after the failing one should still be notified")
                .containsExactly(new Event("registered", "weather", spec));
    }

    @Test
    void testRemovedListenerReceivesNoFurtherEvents() {
        registry.removeListener(listener);
        registry.put("weather", spec("getWeather"));

        assertThat(listener.events).isEmpty();
    }

    private static AiToolSpec spec(String name) {
        return new AiToolSpec(name, name + " description", null, null, Map.of(), null, null, null);
    }

    private record Event(String type, String tag, AiToolSpec spec) {
    }

    private static final class RecordingListener implements AiToolRegistryListener {
        private final List<Event> events = new ArrayList<>();

        @Override
        public void toolRegistered(String tag, AiToolSpec spec) {
            events.add(new Event("registered", tag, spec));
        }

        @Override
        public void toolDeregistered(String tag, AiToolSpec spec) {
            events.add(new Event("deregistered", tag, spec));
        }
    }
}
