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
package org.apache.camel.component.metrics;

import org.apache.camel.spi.Metadata;

public final class MetricsConstants {

    public static final String HEADER_PREFIX = "CamelMetrics";
    @Metadata(description = "The timer action, start or stop, for the timer type. It replaces the action option.",
              javaType = "org.apache.camel.component.metrics.MetricsTimerAction")
    public static final String HEADER_TIMER_ACTION = HEADER_PREFIX + "TimerAction";
    @Metadata(description = "The number of events to mark on the meter, for the meter type. It replaces the mark option.",
              javaType = "long")
    public static final String HEADER_METER_MARK = HEADER_PREFIX + "MeterMark";
    @Metadata(description = "The value to add to the histogram, for the histogram type. It replaces the value option.",
              javaType = "long")
    public static final String HEADER_HISTOGRAM_VALUE = HEADER_PREFIX + "HistogramValue";
    @Metadata(description = "The amount to subtract from the counter, for the counter type. It replaces the decrement"
                            + " option, and is used only when no increment is set.",
              javaType = "long")
    public static final String HEADER_COUNTER_DECREMENT = HEADER_PREFIX + "CounterDecrement";
    @Metadata(description = "The amount to add to the counter, for the counter type. It replaces the increment option.",
              javaType = "long")
    public static final String HEADER_COUNTER_INCREMENT = HEADER_PREFIX + "CounterIncrement";
    @Metadata(description = "The object that the gauge returns as its value, for the gauge type. It replaces the value of"
                            + " the gauge, or registers the gauge if it does not exist yet.",
              javaType = "Object")
    public static final String HEADER_GAUGE_SUBJECT = HEADER_PREFIX + "GaugeSubject";
    @Metadata(description = "The name of the metric. If not empty, it replaces the metricsName of the endpoint.",
              javaType = "String")
    public static final String HEADER_METRIC_NAME = HEADER_PREFIX + "Name";

    private MetricsConstants() {
    }

}
