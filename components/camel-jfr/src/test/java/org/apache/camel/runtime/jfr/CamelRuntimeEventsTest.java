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
package org.apache.camel.runtime.jfr;

import java.util.List;

import jdk.jfr.consumer.RecordedEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CamelRuntimeEventsTest extends JfrRecordingTestSupport {

    @Test
    void routeEventIsRecordedWithFields() throws Exception {
        List<RecordedEvent> events = recordAndRun(new Class<?>[] { CamelRouteEvent.class }, () -> {
            CamelRouteEvent event = new CamelRouteEvent();
            event.routeId = "route1";
            event.exchangeId = "ex1";
            event.failed = false;
            event.begin();
            event.commit();
        });

        assertThat(events)
                .filteredOn(e -> "org.apache.camel.route".equals(e.getEventType().getName()))
                .hasSize(1)
                .first()
                .satisfies(e -> {
                    assertThat(e.getString("routeId")).isEqualTo("route1");
                    assertThat(e.getString("exchangeId")).isEqualTo("ex1");
                    assertThat(e.getBoolean("failed")).isFalse();
                });
    }
}
