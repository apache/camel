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
package org.apache.camel.component.plc4x;

import java.util.Map;

import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.plc4x.java.scraper.triggeredscraper.TriggeredScraperImpl;
import org.apache.plc4x.java.scraper.triggeredscraper.triggerhandler.collector.TriggerCollectorImpl;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A stopped triggered consumer must stop its scraper, or the scraper keeps reading the PLC and sending exchanges to the
 * route, also after the route is started again with a new consumer (duplicates).
 */
class Plc4XConsumerStopTest {

    @Test
    void testStopStopsTheScraper() throws Exception {
        Plc4XEndpoint endpoint = mock(Plc4XEndpoint.class);
        when(endpoint.getCamelContext()).thenReturn(new DefaultCamelContext());
        when(endpoint.getTrigger()).thenReturn("(PLC4X_TRIGGER_VAR,10,(%DB1:DBX0.0:BOOL)==(true))");
        when(endpoint.getTags()).thenReturn(Map.of("tag1", "%DB1:DBW2:INT"));
        when(endpoint.getPeriod()).thenReturn(1000);
        when(endpoint.getUri()).thenReturn("mock:plc");

        try (MockedConstruction<TriggeredScraperImpl> scrapers = mockConstruction(TriggeredScraperImpl.class);
             MockedConstruction<TriggerCollectorImpl> collectors = mockConstruction(TriggerCollectorImpl.class)) {
            Plc4XConsumer consumer = new Plc4XConsumer(endpoint, mock(Processor.class));
            consumer.start();

            assertEquals(1, scrapers.constructed().size());
            TriggeredScraperImpl scraper = scrapers.constructed().get(0);
            TriggerCollectorImpl collector = collectors.constructed().get(0);
            verify(scraper).start();
            verify(scraper, never()).stop();

            consumer.stop();

            verify(scraper).stop();
            verify(collector).stop();
        }
    }
}
