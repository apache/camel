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
package org.apache.camel.opentelemetry.metrics.messagehistory;

import java.util.concurrent.TimeUnit;

import io.opentelemetry.api.metrics.LongHistogram;
import org.apache.camel.Message;
import org.apache.camel.NamedNode;
import org.apache.camel.Route;
import org.apache.camel.support.DefaultMessageHistory;

public class OpenTelemetryMessageHistory extends DefaultMessageHistory {

    private final Route route;
    private final OpenTelemetryHistoryNamingStrategy namingStrategy;
    private final TimeUnit durationUnit;
    private final LongHistogram timer;

    public OpenTelemetryMessageHistory(LongHistogram timer, TimeUnit durationUnit, Route route, NamedNode namedNode,
                                       OpenTelemetryHistoryNamingStrategy namingStrategy, Message message) {
        super(route.getId(), namedNode, message);
        this.timer = timer;
        this.durationUnit = durationUnit;
        this.route = route;
        this.namingStrategy = namingStrategy;
    }

    @Override
    public void nodeProcessingDone() {
        super.nodeProcessingDone();
        this.timer.record(durationUnit.convert(getElapsed(), TimeUnit.MILLISECONDS),
                namingStrategy.getAttributes(route, getNode()));
    }

    @Override
    public String toString() {
        return "OpenTelemetryMessageHistory[routeId=" + getRouteId() + ", node=" + getNode().getId() + ']';
    }
}
