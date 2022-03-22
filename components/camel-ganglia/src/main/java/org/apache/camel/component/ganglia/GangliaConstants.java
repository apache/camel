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
package org.apache.camel.component.ganglia;

import org.apache.camel.spi.Metadata;

public final class GangliaConstants {

    @Metadata(description = "The group that the metric belongs to.", javaType = "String")
    public static final String GROUP_NAME = "CamelGangliaGroupName";
    @Metadata(description = "The name to use for the metric.", javaType = "String")
    public static final String METRIC_NAME = "CamelGangliaMetricName";
    @Metadata(description = "The type of value", javaType = "info.ganglia.gmetric4j.gmetric.GMetricType")
    public static final String METRIC_TYPE = "CamelGangliaMetricType";
    @Metadata(description = "The slope", javaType = "info.ganglia.gmetric4j.gmetric.GMetricSlope")
    public static final String METRIC_SLOPE = "CamelGangliaMetricSlope";
    @Metadata(description = "Any unit of measurement that qualifies the metric, e.g. widgets, litres, bytes. Do not include a prefix such as k\n"
                            +
                            " (kilo) or m (milli), other tools may scale the units later. The value should be unscaled.",
              javaType = "String")
    public static final String METRIC_UNITS = "CamelGangliaMetricUnits";
    @Metadata(description = "Maximum time in seconds that the value can be considered current. After this, Ganglia considers the value to have\n"
                            +
                            " expired.",
              javaType = "Integer")
    public static final String METRIC_TMAX = "CamelGangliaMetricTmax";
    @Metadata(description = "Minumum time in seconds before Ganglia will purge the metric value if it expires. Set to 0 and the value will\n"
                            +
                            " remain in Ganglia indefinitely until a gmond agent restart.",
              javaType = "Integer")
    public static final String METRIC_DMAX = "CamelGangliaMetricDmax";

    private GangliaConstants() {
    }

}
