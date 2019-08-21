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

public final class MicroProfileMetricsConstants {

    public static final String HEADER_PREFIX = "CamelMicroProfileMetrics";

    public static final String HEADER_COUNTER_INCREMENT = HEADER_PREFIX + "CounterIncrement";
    public static final String HEADER_GAUGE_INCREMENT = HEADER_PREFIX + "GaugeIncrement";
    public static final String HEADER_GAUGE_DECREMENT = HEADER_PREFIX + "GaugeDecrement";
    public static final String HEADER_HISTOGRAM_VALUE = HEADER_PREFIX + "HistogramValue";
    public static final String HEADER_METER_MARK = HEADER_PREFIX + "MeterMark";
    public static final String HEADER_METRIC_DESCRIPTION = HEADER_PREFIX + "Description";
    public static final String HEADER_METRIC_DISPLAY_NAME = HEADER_PREFIX + "DisplayName";
    public static final String HEADER_METRIC_NAME = HEADER_PREFIX + "Name";
    public static final String HEADER_METRIC_TAGS = HEADER_PREFIX + "Tags";
    public static final String HEADER_METRIC_TYPE = HEADER_PREFIX + "Type";
    public static final String HEADER_METRIC_UNIT = HEADER_PREFIX + "Units";
    public static final String HEADER_TIMER_ACTION = HEADER_PREFIX + "TimerAction";

    public static final String DEFAULT_CAMEL_MESSAGE_HISTORY_METRIC_NAME = "org.apache.camel.message.history";
    public static final String DEFAULT_CAMEL_ROUTE_POLICY_METRIC_NAME = "org.apache.camel.route";
    public static final String DEFAULT_CAMEL_EXCHANGE_EVENT_METRIC_NAME = "org.apache.camel.exchange";
    public static final String DEFAULT_CAMEL_ROUTES_ADDED_METRIC_NAME = "org.apache.camel.route.total";
    public static final String DEFAULT_CAMEL_ROUTES_RUNNING_METRIC_NAME = "org.apache.camel.route.running.total";

    public static final String ROUTE_ID_TAG = "routeId";
    public static final String NODE_ID_TAG = "nodeId";
    public static final String FAILED_TAG = "failed";
    public static final String CAMEL_CONTEXT_TAG = "camelContext";
    public static final String EVENT_TYPE_TAG = "eventType";
    public static final String METRIC_REGISTRY_NAME = "metricRegistry";

    public static final String SERVICE_NAME = "serviceName";
    public static final String ENDPOINT_NAME = "endpointName";

    private MicroProfileMetricsConstants() {
    }
}
