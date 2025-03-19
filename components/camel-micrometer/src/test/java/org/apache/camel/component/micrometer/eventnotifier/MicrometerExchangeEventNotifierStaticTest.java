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
package org.apache.camel.component.micrometer.eventnotifier;

import java.util.Set;

import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Statistic;
import org.apache.camel.component.micrometer.MicrometerConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class MicrometerExchangeEventNotifierStaticTest extends MicrometerExchangeEventNotifierDynamicTest {

    private static final String MOCK_OUT = "mock://out";
    private static final String DIRECT_IN = "direct://in";

    @Override
    protected AbstractMicrometerEventNotifier<?> getEventNotifier() {
        var notifier = (MicrometerExchangeEventNotifier) super.getEventNotifier();
        notifier.setBaseEndpointURI(true);
        return notifier;
    }

    @Test
    public void testEventNotifier() throws Exception {
        int count = 10;
        MockEndpoint mock = getMockEndpoint(MOCK_OUT);
        mock.expectedMessageCount(count);

        for (int i = 0; i < count; i++) {
            template.sendBody(DIRECT_IN, i);
        }

        mock.assertIsSatisfied();

        // Let's calculate the number of entries hold by the meter registry.
        // We need to scan the entire data structure to make sure only one
        // entry exists.
        Set<MeterRegistry> set = meterRegistry.getRegistries();
        assertEquals(2, set.size());
        for (MeterRegistry mr : set) {
            assertEquals(6, mr.getMeters().size());
            int counter = 0;
            for (Meter m : mr.getMeters()) {
                if (m.getId().getName().equals(MicrometerConstants.DEFAULT_CAMEL_EXCHANGE_EVENT_METER_NAME) &&
                        m.getId().getTag("endpointName").equals("my://component")) {
                    counter++;
                    Measurement entry = null;
                    for (Measurement me : m.measure()) {
                        if (Statistic.COUNT.equals(Statistic.valueOf(me.getStatistic().name()))) {
                            entry = me;
                        }
                    }
                    assertNotNull(entry);
                    assertEquals(count, entry.getValue());
                }
            }
            assertEquals(1, counter, "Only one measure should be present for 'my://component' endpoint.");
        }
    }

}
