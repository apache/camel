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

public final class MicrometerConstants {

    public static final String HEADER_PREFIX = "CamelMetrics";
    public static final String HEADER_TIMER_ACTION = HEADER_PREFIX + "TimerAction";
    public static final String HEADER_HISTOGRAM_VALUE = HEADER_PREFIX + "HistogramValue";
    public static final String HEADER_COUNTER_DECREMENT = HEADER_PREFIX + "CounterDecrement";
    public static final String HEADER_COUNTER_INCREMENT = HEADER_PREFIX + "CounterIncrement";
    public static final String HEADER_METRIC_NAME = HEADER_PREFIX + "Name";
    public static final String HEADER_METRIC_TAGS = HEADER_PREFIX + "Tags";

    public static final String DEFAULT_CAMEL_MESSAGE_HISTORY_METER_NAME = "CamelMessageHistory";
    public static final String DEFAULT_CAMEL_ROUTE_POLICY_METER_NAME = "CamelRoutePolicy";
    public static final String DEFAULT_CAMEL_EXCHANGE_EVENT_METER_NAME = "CamelExchangeEventNotifier";
    public static final String DEFAULT_CAMEL_ROUTES_ADDED = "CamelRoutesAdded";
    public static final String DEFAULT_CAMEL_ROUTES_RUNNING = "CamelRoutesRunning";

    public static final String ROUTE_ID_TAG = "routeId";
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
