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
package org.apache.camel;

import jakarta.xml.bind.annotation.XmlEnum;

/**
 * Controls the granularity of JMX performance statistics collected for a running {@link CamelContext}.
 * <p/>
 * Statistics are collected by the Camel management layer and exposed via MBeans. Finer granularity (down to individual
 * {@link Processor}s) gives richer observability but uses more memory. {@link #Extended} adds extra counters such as
 * idle-since timestamps.
 * <p/>
 * Configure via {@code ManagementAgent.setStatisticsLevel(ManagementStatisticsLevel)} or the property
 * {@code camel.main.jmx-management-statistics-level}. The default is {@link #Default}.
 *
 * @see ManagementMBeansLevel
 */
@XmlEnum
public enum ManagementStatisticsLevel {

    /** Extended statistics including additional performance metrics. */
    Extended,
    /** Default statistics for context, routes, and processors. */
    Default,
    /** Statistics for the context and routes only (no processors). */
    RoutesOnly,
    /** No statistics collected. */
    Off;

    public boolean isDefaultOrExtended() {
        return ordinal() == Default.ordinal() || ordinal() == Extended.ordinal();
    }

    public boolean isExtended() {
        return ordinal() == Extended.ordinal();
    }

}
