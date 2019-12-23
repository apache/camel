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
package org.apache.camel.component.micrometer.messagehistory;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.apache.camel.Message;
import org.apache.camel.MessageHistory;
import org.apache.camel.NamedNode;
import org.apache.camel.Route;
import org.apache.camel.support.DefaultMessageHistory;

/**
 * A micrometer metrics based {@link MessageHistory}. This could also use {@link #getElapsed()}
 * provided by the super class, but Micrometer can potentially use other {@link io.micrometer.core.instrument.Clock clocks}
 * and measures in nano-second precision.
 */
public class MicrometerMessageHistory extends DefaultMessageHistory {

    private final Route route;
    private final Timer.Sample sample;
    private final MeterRegistry meterRegistry;
    private final MicrometerMessageHistoryNamingStrategy namingStrategy;

    public MicrometerMessageHistory(MeterRegistry meterRegistry, Route route, NamedNode namedNode,
                                    MicrometerMessageHistoryNamingStrategy namingStrategy, long timestamp, Message message) {
        super(route.getId(), namedNode, timestamp, message);
        this.meterRegistry = meterRegistry;
        this.route = route;
        this.namingStrategy = namingStrategy;
        this.sample = Timer.start(meterRegistry);
    }

    @Override
    public void nodeProcessingDone() {
        super.nodeProcessingDone();
        Timer timer = Timer.builder(namingStrategy.getName(route, getNode()))
                .tags(namingStrategy.getTags(route, getNode()))
                .description(getNode().getDescriptionText())
                .register(meterRegistry);
        sample.stop(timer);
    }

    @Override
    public String toString() {
        return "MicrometerMessageHistory[routeId=" + getRouteId() + ", node=" + getNode().getId() + ']';
    }

}
