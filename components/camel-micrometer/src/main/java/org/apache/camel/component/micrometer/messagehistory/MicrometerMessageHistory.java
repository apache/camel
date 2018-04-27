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
package org.apache.camel.component.micrometer.messagehistory;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import org.apache.camel.MessageHistory;
import org.apache.camel.NamedNode;
import org.apache.camel.impl.DefaultMessageHistory;

import java.time.Duration;
import java.util.Arrays;

/**
 * A micrometer metrics based {@link MessageHistory}
 */
public class MicrometerMessageHistory extends DefaultMessageHistory {

    private final Timer.Sample sample;
    private final MeterRegistry meterRegistry;
    private final String name;

    private Duration minimumExpectedValue = Duration.ofMillis(1L);
    private Duration maximumExpectedValue = Duration.ofSeconds(30L);

    public MicrometerMessageHistory(MeterRegistry meterRegistry, String routeId, NamedNode namedNode, String name, long timestamp) {
        super(routeId, namedNode, timestamp);
        this.meterRegistry = meterRegistry;
        this.name = name;
        this.sample = Timer.start(meterRegistry);
    }

    @Override
    public void nodeProcessingDone() {
        super.nodeProcessingDone();
        Timer timer = Timer.builder(name)
                .tag("camelService", "messageHistory")
                .tag("routeId", getRouteId())
                .tag("nodeId", getNode().getId())
                .register(meterRegistry);
        sample.stop(timer);
    }

    public String toString() {
        return "MicrometerMessageHistory[routeId=" + getRouteId() + ", node=" + getNode().getId() + ']';
    }

}
