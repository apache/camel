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
package org.apache.camel.component.microprofile.metrics.message.history;

import org.apache.camel.Message;
import org.apache.camel.NamedNode;
import org.apache.camel.Route;
import org.apache.camel.support.DefaultMessageHistory;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetadataBuilder;
import org.eclipse.microprofile.metrics.MetricRegistry;

import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.MESSAGE_HISTORY_DESCRIPTION;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.MESSAGE_HISTORY_DISPLAY_NAME;
import static org.eclipse.microprofile.metrics.Timer.Context;

public class MicroProfileMetricsMessageHistory extends DefaultMessageHistory {
    private final Context context;

    public MicroProfileMetricsMessageHistory(MetricRegistry metricRegistry, Route route, NamedNode namedNode,
            MicroProfileMetricsMessageHistoryNamingStrategy namingStrategy, long timestamp, Message message) {
        super(route.getId(), namedNode, timestamp, message);

        Metadata routeNodeMetadata = new MetadataBuilder()
            .withName(namingStrategy.getName(route, getNode()))
            .withDisplayName(MESSAGE_HISTORY_DISPLAY_NAME)
            .withDescription(MESSAGE_HISTORY_DESCRIPTION)
            .build();
        this.context = metricRegistry.timer(routeNodeMetadata, namingStrategy.getTags(route, getNode())).time();
    }

    @Override
    public void nodeProcessingDone() {
        super.nodeProcessingDone();
        context.stop();
    }

    @Override
    public String toString() {
        return "MicroProfileMetricsMessageHistory[routeId=" + getRouteId() + ", node=" + getNode().getId() + ']';
    }

}
