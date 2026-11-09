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
package org.apache.camel.component.micrometer.json;

import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.camel.component.micrometer.messagehistory.MicrometerMessageHistoryService;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MicrometerModuleTest {

    private static final String JSON
            = """
                    {"version":"1.0.5",\
                    "gauges":[{"id":{"name":"my.gauge","tags":[]},"value":7.5}],\
                    "counters":[{"id":{"name":"my.counter","tags":[{"k":"v"}]},"count":3.0}],\
                    "functionCounters":[{"id":{"name":"my.fc","tags":[]},"count":5.0}],\
                    "timers":[{"id":{"name":"my.timer","tags":[]},"count":1,"max":2000.0,"mean":2000.0,"total":2000.0}],\
                    "functionTimers":[{"id":{"name":"my.ft","tags":[]},"count":2.0,"mean":2000.0,"total":4000.0}],\
                    "longTaskTimers":[{"id":{"name":"my.ltt","tags":[]},"activeTasks":0,"duration":0.0}],\
                    "distributionSummaries":[{"id":{"name":"my.summary","tags":[]},"count":1,"max":4.0E-6,"mean":4.0E-6,"total":4.0E-6}]}""";

    private static final String PRETTY_COUNTER_ONLY = """
            {
              "version" : "1.0.5",
              "gauges" : [ ],
              "counters" : [ {
                "id" : {
                  "name" : "my.counter",
                  "tags" : [ {
                    "k" : "v"
                  } ]
                },
                "count" : 3.0
              } ],
              "functionCounters" : [ ],
              "timers" : [ ],
              "functionTimers" : [ ],
              "longTaskTimers" : [ ],
              "distributionSummaries" : [ ]
            }""";

    private static MicrometerMessageHistoryService start(SimpleMeterRegistry registry, boolean pretty) {
        MicrometerMessageHistoryService service = new MicrometerMessageHistoryService();
        service.setCamelContext(new DefaultCamelContext());
        service.setMeterRegistry(registry);
        service.setSkipCamelInfo(true);
        service.setPrettyPrint(pretty);
        service.start();
        return service;
    }

    @Test
    void theJsonOfEachMeterTypeIsUnchanged() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, new MockClock());
        registry.counter("my.counter", "k", "v").increment(3);
        registry.gauge("my.gauge", 7.5);
        registry.timer("my.timer").record(2, TimeUnit.SECONDS);
        registry.summary("my.summary").record(4);
        registry.more().longTaskTimer("my.ltt");
        FunctionCounter.builder("my.fc", 1, x -> 5d).register(registry);
        FunctionTimer.builder("my.ft", 1, x -> 2L, x -> 4d, TimeUnit.SECONDS).register(registry);

        assertEquals(JSON, start(registry, false).dumpStatisticsAsJson());
    }

    @Test
    void thePrettyPrintedJsonIsUnchanged() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, new MockClock());
        registry.counter("my.counter", "k", "v").increment(3);

        assertEquals(PRETTY_COUNTER_ONLY, start(registry, true).dumpStatisticsAsJson());
    }
}
