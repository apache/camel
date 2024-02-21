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
package org.apache.camel.component.aws2.cw;

import org.apache.camel.spi.Metadata;

/**
 * Constants used in Camel AWS CloudWatch module SDK v2
 */
public interface Cw2Constants {
    @Metadata(description = "The Amazon CW metric namespace.", javaType = "String")
    String METRIC_NAMESPACE = "CamelAwsCwMetricNamespace";
    @Metadata(description = "The Amazon CW metric name.", javaType = "String")
    String METRIC_NAME = "CamelAwsCwMetricName";
    @Metadata(description = "The Amazon CW metric value.", javaType = "Double")
    String METRIC_VALUE = "CamelAwsCwMetricValue";
    @Metadata(description = "The Amazon CW metric unit.", javaType = "String")
    String METRIC_UNIT = "CamelAwsCwMetricUnit";
    @Metadata(description = "The Amazon CW metric timestamp.", javaType = "Date")
    String METRIC_TIMESTAMP = "CamelAwsCwMetricTimestamp";
    @Metadata(description = "A map of dimension names and dimension values.", javaType = "Map<String, String>")
    String METRIC_DIMENSIONS = "CamelAwsCwMetricDimensions";
    @Metadata(description = "The Amazon CW metric dimension name.", javaType = "String")
    String METRIC_DIMENSION_NAME = "CamelAwsCwMetricDimensionName";
    @Metadata(description = "The Amazon CW metric dimension value.", javaType = "String")
    String METRIC_DIMENSION_VALUE = "CamelAwsCwMetricDimensionValue";
}
