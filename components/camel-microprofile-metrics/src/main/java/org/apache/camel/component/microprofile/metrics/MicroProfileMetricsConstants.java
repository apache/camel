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
package org.apache.camel.component.microprofile.metrics;

import org.apache.camel.ServiceStatus;

public final class MicroProfileMetricsConstants {

    public static final String HEADER_PREFIX = "CamelMicroProfileMetrics";

    public static final String HEADER_COUNTER_INCREMENT = HEADER_PREFIX + "CounterIncrement";
    public static final String HEADER_GAUGE_INCREMENT = HEADER_PREFIX + "GaugeIncrement";
    public static final String HEADER_GAUGE_DECREMENT = HEADER_PREFIX + "GaugeDecrement";
    public static final String HEADER_GAUGE_VALUE = HEADER_PREFIX + "GaugeValue";
    public static final String HEADER_HISTOGRAM_VALUE = HEADER_PREFIX + "HistogramValue";
    public static final String HEADER_METER_MARK = HEADER_PREFIX + "MeterMark";
    public static final String HEADER_METRIC_DESCRIPTION = HEADER_PREFIX + "Description";
    public static final String HEADER_METRIC_DISPLAY_NAME = HEADER_PREFIX + "DisplayName";
    public static final String HEADER_METRIC_NAME = HEADER_PREFIX + "Name";
    public static final String HEADER_METRIC_TAGS = HEADER_PREFIX + "Tags";
    public static final String HEADER_METRIC_TYPE = HEADER_PREFIX + "Type";
    public static final String HEADER_METRIC_UNIT = HEADER_PREFIX + "Units";
    public static final String HEADER_TIMER_ACTION = HEADER_PREFIX + "TimerAction";

    public static final String CAMEL_METRIC_PREFIX = "camel";
    public static final String CAMEL_CONTEXT_METRIC_NAME = CAMEL_METRIC_PREFIX + ".context";

    public static final String PROCESSING_METRICS_SUFFIX = ".processing";

    public static final String DEFAULT_CAMEL_MESSAGE_HISTORY_METRIC_NAME = CAMEL_METRIC_PREFIX + ".message.history" + PROCESSING_METRICS_SUFFIX;
    public static final String DEFAULT_CAMEL_ROUTE_POLICY_METRIC_NAME = CAMEL_METRIC_PREFIX + ".route";
    public static final String DEFAULT_CAMEL_ROUTE_POLICY_PROCESSING_METRIC_NAME = DEFAULT_CAMEL_ROUTE_POLICY_METRIC_NAME + PROCESSING_METRICS_SUFFIX;
    public static final String DEFAULT_CAMEL_EXCHANGE_EVENT_METRIC_NAME = CAMEL_METRIC_PREFIX + ".exchange";
    public static final String DEFAULT_CAMEL_EXCHANGE_EVENT_PROCESSING_METRIC_NAME = DEFAULT_CAMEL_EXCHANGE_EVENT_METRIC_NAME + PROCESSING_METRICS_SUFFIX;
    public static final String DEFAULT_CAMEL_ROUTES_ADDED_METRIC_NAME = CAMEL_METRIC_PREFIX + ".route.count";
    public static final String DEFAULT_CAMEL_ROUTES_RUNNING_METRIC_NAME = CAMEL_METRIC_PREFIX + ".route.running.count";

    public static final String CAMEL_CONTEXT_UPTIME_METRIC_NAME = CAMEL_CONTEXT_METRIC_NAME + ".uptime";
    public static final String CAMEL_CONTEXT_UPTIME_DISPLAY_NAME = "Camel Context uptime";
    public static final String CAMEL_CONTEXT_UPTIME_DESCRIPTION = "The amount of time since the Camel Context was started.";

    public static final String CAMEL_CONTEXT_STATUS_METRIC_NAME = CAMEL_CONTEXT_METRIC_NAME + ".status";
    public static final String CAMEL_CONTEXT_STATUS_DISPLAY_NAME = "Camel Context status";
    public static final String CAMEL_CONTEXT_STATUS_DESCRIPTION = "The status of the Camel Context represented by the enum ordinal of " + ServiceStatus.class.getName() + ".";

    public static final String EXCHANGES_METRIC_PREFIX = ".exchanges";
    public static final String EXCHANGES_COMPLETED_METRIC_NAME = EXCHANGES_METRIC_PREFIX + ".completed.total";
    public static final String EXCHANGES_COMPLETED_DISPLAY_NAME = "Exchanges completed";
    public static final String EXCHANGES_COMPLETED_DESCRIPTION = "The total number of completed exchanges for a route or Camel Context";

    public static final String EXCHANGES_FAILED_METRIC_NAME = EXCHANGES_METRIC_PREFIX + ".failed.total";
    public static final String EXCHANGES_FAILED_DISPLAY_NAME = "Exchanges failed";
    public static final String EXCHANGES_FAILED_DESCRIPTION = "The total number of failed exchanges for a route or Camel Context";

    public static final String EXCHANGES_TOTAL_METRIC_NAME = EXCHANGES_METRIC_PREFIX + ".total";
    public static final String EXCHANGES_TOTAL_DISPLAY_NAME = "Exchanges total";
    public static final String EXCHANGES_TOTAL_DESCRIPTION = "The total number of exchanges for a route or Camel Context";

    public static final String EXCHANGES_INFLIGHT_METRIC_NAME = EXCHANGES_METRIC_PREFIX + ".inflight.count";
    public static final String EXCHANGES_INFLIGHT_DISPLAY_NAME = "Exchanges inflight";
    public static final String EXCHANGES_INFLIGHT_DESCRIPTION = "The count of exchanges inflight for a route or Camel Context";

    public static final String EXCHANGES_EXTERNAL_REDELIVERIES_METRIC_NAME = ".externalRedeliveries.total";
    public static final String EXCHANGES_EXTERNAL_REDELIVERIES_DISPLAY_NAME = "Exchanges external redeliveries";
    public static final String EXCHANGES_EXTERNAL_REDELIVERIES_DESCRIPTION = "The total number of external redeliveries for a route or Camel Context";

    public static final String EXCHANGES_FAILURES_HANDLED_METRIC_NAME = ".failuresHandled.total";
    public static final String EXCHANGES_FAILURES_HANDLED_DISPLAY_NAME = "Exchanges failures handled";
    public static final String EXCHANGES_FAILURES_HANDLED_DESCRIPTION = "The total number of failures handled for a route or Camel Context";

    public static final String ROUTES_ADDED_DISPLAY_NAME = "Routes count";
    public static final String ROUTES_ADDED_DESCRIPTION = "The count of routes.";
    public static final String ROUTES_RUNNING_DISPLAY_NAME = "Routes running count";
    public static final String ROUTES_RUNNING_DESCRIPTION = "The count of running routes.";

    public static final String MESSAGE_HISTORY_DISPLAY_NAME = "Route node processing time";
    public static final String MESSAGE_HISTORY_DESCRIPTION = "The time taken to process an individual route node";

    public static final String ROUTE_ID_TAG = "routeId";
    public static final String NODE_ID_TAG = "nodeId";
    public static final String CAMEL_CONTEXT_TAG = "camelContext";
    public static final String EVENT_TYPE_TAG = "eventType";
    public static final String METRIC_REGISTRY_NAME = "metricRegistry";

    public static final String ENDPOINT_NAME = "endpointName";

    private MicroProfileMetricsConstants() {
    }
}
