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

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import org.apache.camel.NamedNode;
import org.apache.camel.Route;

import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.CAMEL_CONTEXT_ATTRIBUTE;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.DEFAULT_CAMEL_MESSAGE_HISTORY_METER_NAME;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.KIND_ATTRIBUTE;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.KIND_HISTORY;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.NODE_ID_ATTRIBUTE;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.ROUTE_ID_ATTRIBUTE;

public interface OpenTelemetryHistoryNamingStrategy {

    OpenTelemetryHistoryNamingStrategy DEFAULT = () -> DEFAULT_CAMEL_MESSAGE_HISTORY_METER_NAME;

    String getName();

    default String formatName(String name) {
        return name;
    }

    default Attributes getAttributes(Route route, NamedNode node) {
        return Attributes.of(
                AttributeKey.stringKey(CAMEL_CONTEXT_ATTRIBUTE), route.getCamelContext().getName(),
                AttributeKey.stringKey(KIND_ATTRIBUTE), KIND_HISTORY,
                AttributeKey.stringKey(ROUTE_ID_ATTRIBUTE), route.getId(),
                AttributeKey.stringKey(NODE_ID_ATTRIBUTE), node.getId());
    }
}
