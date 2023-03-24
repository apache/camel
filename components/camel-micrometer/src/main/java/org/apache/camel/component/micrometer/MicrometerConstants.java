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
package org.apache.camel.component.micrometer;

import java.util.function.Predicate;

import io.micrometer.core.instrument.Meter;
import org.apache.camel.spi.Metadata;

public final class MicrometerConstants {

    public static final String HEADER_PREFIX = "CamelMetrics";
    @Metadata(description = "Override timer action in URI",
              javaType = "org.apache.camel.component.micrometer.MicrometerTimerAction")
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
    @Metadata(description = "To augment meter tags defined as URI parameters", javaType = "java.lang.Iterable<Tag>")
    public static final String HEADER_METRIC_TAGS = HEADER_PREFIX + "Tags";

    public static final String DEFAULT_CAMEL_MESSAGE_HISTORY_METER_NAME = "camel.message.history";
    public static final String DEFAULT_CAMEL_ROUTE_POLICY_EXCHANGES_FAILED_METER_NAME = "camel.exchanges.failed";
    public static final String DEFAULT_CAMEL_ROUTE_POLICY_EXCHANGES_SUCCEEDED_METER_NAME = "camel.exchanges.succeeded";
    public static final String DEFAULT_CAMEL_ROUTE_POLICY_EXCHANGES_TOTAL_METER_NAME = "camel.exchanges.total";
    public static final String DEFAULT_CAMEL_ROUTE_POLICY_EXCHANGES_FAILURES_HANDLED_METER_NAME
            = "camel.exchanges.failures.handled";
    public static final String DEFAULT_CAMEL_ROUTE_POLICY_EXCHANGES_EXTERNAL_REDELIVERIES_METER_NAME
            = "camel.exchanges.external.redeliveries";
    public static final String DEFAULT_CAMEL_ROUTE_POLICY_METER_NAME = "camel.route.policy";
    public static final String DEFAULT_CAMEL_ROUTE_POLICY_LONGMETER_NAME = "camel.route.policy.long.task";
    public static final String DEFAULT_CAMEL_EXCHANGE_EVENT_METER_NAME = "camel.exchange.event.notifier";
    public static final String DEFAULT_CAMEL_ROUTES_ADDED = "camel.routes.added";
    public static final String DEFAULT_CAMEL_ROUTES_RUNNING = "camel.routes.running";
    public static final String DEFAULT_CAMEL_ROUTES_EXCHANGES_INFLIGHT = "camel.exchanges.inflight";

    public static final String ROUTE_ID_TAG = "routeId";
    public static final String ROUTE_DESCRIPTION_TAG = "routeDescription";
    public static final String NODE_ID_TAG = "nodeId";
    public static final String FAILED_TAG = "failed";
    public static final String CAMEL_CONTEXT_TAG = "camelContext";
    public static final String EVENT_TYPE_TAG = "eventType";
    public static final String METRICS_REGISTRY_NAME = "metricsRegistry";

    public static final String SERVICE_NAME = "serviceName";
    public static final String ENDPOINT_NAME = "endpointName";

    public static final Predicate<Meter.Id> CAMEL_METERS = id -> id.getTag(CAMEL_CONTEXT_TAG) != null;
    public static final Predicate<Meter.Id> TIMERS = id -> id.getType() == Meter.Type.TIMER;
    public static final Predicate<Meter.Id> DISTRIBUTION_SUMMARIES = id -> id.getType() == Meter.Type.DISTRIBUTION_SUMMARY;
    public static final Predicate<Meter.Id> ALWAYS = id -> true;

    private MicrometerConstants() {
    }

}
