/**
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

public final class MetricsConstants {

    public static final String HEADER_PERFIX = "CamelMetrics";
    public static final String HEADER_TIMER_ACTION = HEADER_PERFIX + "TimerAction";
    public static final String HEADER_METER_MARK = HEADER_PERFIX + "MeterMark";
    public static final String HEADER_HISTOGRAM_VALUE = HEADER_PERFIX + "HistogramValue";
    public static final String HEADER_COUNTER_DECREMENT = HEADER_PERFIX + "CounterDecrement";
    public static final String HEADER_COUNTER_INCREMENT = HEADER_PERFIX + "CounterIncrement";
    public static final String HEADER_METRIC_NAME = HEADER_PERFIX + "Name";

    private MetricsConstants() {
    }

}
