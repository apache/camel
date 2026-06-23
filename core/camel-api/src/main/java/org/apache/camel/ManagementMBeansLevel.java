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
 * Controls which Camel objects are registered as JMX MBeans for management and monitoring.
 * <p/>
 * When JMX management is active, Camel can expose the {@link CamelContext}, its {@link Route}s, and individual
 * {@link Processor}s as MBeans. Registering many processors improves observability but increases heap usage; this enum
 * lets operators trade off granularity against overhead.
 * <p/>
 * Configure via {@code ManagementAgent.setMBeansLevel(ManagementMBeansLevel)} or the property
 * {@code camel.main.jmx-management-mbeans-level}.
 *
 * @see   ManagementStatisticsLevel
 * @since 3.17
 */
@XmlEnum
public enum ManagementMBeansLevel {

    /** Only register the CamelContext MBean. */
    ContextOnly,
    /** Register MBeans for the CamelContext and routes. */
    RoutesOnly,
    /** Register MBeans for the CamelContext, routes, and processors. */
    Default;

    public boolean isRoutes() {
        return ordinal() == Default.ordinal() || ordinal() == RoutesOnly.ordinal();
    }

    public boolean isProcessors() {
        return ordinal() == Default.ordinal();
    }
}
