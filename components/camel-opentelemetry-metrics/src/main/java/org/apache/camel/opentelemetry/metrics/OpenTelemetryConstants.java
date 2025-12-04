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

package org.apache.camel.opentelemetry.metrics;

import org.apache.camel.spi.Metadata;

public class OpenTelemetryConstants {

    public static final String HEADER_PREFIX = "CamelMetrics";

    @Metadata(
            description = "Override timer action in URI",
            javaType = "org.apache.camel.opentelemetry.metrics.OpenTelemetryTimerAction")
    public static final String HEADER_TIMER_ACTION = HEADER_PREFIX + "TimerAction";

    @Metadata(description = "Override histogram value in URI", javaType = "long")
    public static final String HEADER_HISTOGRAM_VALUE = HEADER_PREFIX + "HistogramValue";

    @Metadata(description = "Override decrement value in URI", javaType = "Double")
    public static final String HEADER_COUNTER_DECREMENT = HEADER_PREFIX + "CounterDecrement";

    @Metadata(description = "Override increment value in URI", javaType = "Double")
    public static final String HEADER_COUNTER_INCREMENT = HEADER_PREFIX + "CounterIncrement";

    @Metadata(description = "Override name value in URI", javaType = "String")
    public static final String HEADER_METRIC_NAME = HEADER_PREFIX + "Name";

    @Metadata(description = "Override description value in URI", javaType = "String")
    public static final String HEADER_METRIC_DESCRIPTION = HEADER_PREFIX + "Description";

    @Metadata(
            description = "To augment meter attributes defined as URI parameters",
            javaType = "io.opentelemetry.api.common.Attributes")
    public static final String HEADER_METRIC_ATTRIBUTES = HEADER_PREFIX + "Attributes";

    // Route-policy metrics
    public static final String DEFAULT_CAMEL_ROUTE_POLICY_EXCHANGES_FAILED_METER_NAME = "camel.exchanges.failed";
    public static final String DEFAULT_CAMEL_ROUTE_POLICY_EXCHANGES_SUCCEEDED_METER_NAME = "camel.exchanges.succeeded";
    public static final String DEFAULT_CAMEL_ROUTE_POLICY_EXCHANGES_TOTAL_METER_NAME = "camel.exchanges.total";
    public static final String DEFAULT_CAMEL_ROUTE_POLICY_EXCHANGES_FAILURES_HANDLED_METER_NAME =
            "camel.exchanges.failures.handled";
    public static final String DEFAULT_CAMEL_ROUTE_POLICY_EXCHANGES_EXTERNAL_REDELIVERIES_METER_NAME =
            "camel.exchanges.external.redeliveries";
    public static final String DEFAULT_CAMEL_ROUTE_POLICY_METER_NAME = "camel.route.policy";
    public static final String DEFAULT_CAMEL_ROUTE_POLICY_TASKS_ACTIVE = "camel.route.policy.tasks.active";
    public static final String DEFAULT_CAMEL_ROUTE_POLICY_TASKS_DURATION = "camel.route.policy.tasks.duration";

    // Exchange-event metrics
    public static final String DEFAULT_CAMEL_ROUTES_EXCHANGES_INFLIGHT = "camel.exchanges.inflight";
    public static final String DEFAULT_CAMEL_EXCHANGE_ELAPSED_TIMER = "camel.exchange.elapsed";
    public static final String DEFAULT_CAMEL_EXCHANGE_SENT_TIMER = "camel.exchange.sent";
    public static final String DEFAULT_CAMEL_EXCHANGE_LAST_PROCESSED_TIME_INSTRUMENT = "camel.exchanges.last.time";

    // Route-event metrics
    public static final String DEFAULT_CAMEL_ROUTES_ADDED = "camel.routes.added";
    public static final String DEFAULT_CAMEL_ROUTES_RUNNING = "camel.routes.running";
    public static final String DEFAULT_CAMEL_ROUTES_RELOADED = "camel.routes.reloaded";

    // Message-history metric
    public static final String DEFAULT_CAMEL_MESSAGE_HISTORY_METER_NAME = "camel.message.history";

    // OpenTelemetry Attribute keys
    public static final String CAMEL_CONTEXT_ATTRIBUTE = "camelContext";
    public static final String ROUTE_ID_ATTRIBUTE = "routeId";
    public static final String NODE_ID_ATTRIBUTE = "nodeId";
    public static final String FAILED_ATTRIBUTE = "failed";
    public static final String EVENT_TYPE_ATTRIBUTE = "eventType";
    public static final String KIND_ATTRIBUTE = "kind";
    public static final String ENDPOINT_NAME_ATTRIBUTE = "endpointName";

    // OpenTelemetry Attribute values
    public static final String KIND_EXCHANGE = "CamelExchangeEvent";
    public static final String KIND_ROUTE = "CamelRoute";
    public static final String KIND_HISTORY = "CamelMessageHistory";

    private OpenTelemetryConstants() {
        // no-op
    }
}
