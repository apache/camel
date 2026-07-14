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
package org.apache.camel.test.infra.observability.common;

public final class ObservabilityProperties {
    public static final String HOST = "observability.host";

    public static final String PROMETHEUS_CONTAINER = "observability.prometheus.container";
    public static final String PROMETHEUS_PORT = "observability.prometheus.port";
    public static final int DEFAULT_PROMETHEUS_PORT = 9090;

    public static final String VICTORIA_TRACES_CONTAINER = "observability.victoriatraces.container";
    public static final String VICTORIA_TRACES_PORT = "observability.victoria.traces.port";
    public static final int DEFAULT_VICTORIA_TRACES_PORT = 10428;

    public static final String VICTORIA_LOGS_CONTAINER = "observability.victorialogs.container";
    public static final String VICTORIA_LOGS_PORT = "observability.victoria.logs.port";
    public static final int DEFAULT_VICTORIA_LOGS_PORT = 9428;

    public static final String PERSES_CONTAINER = "observability.perses.container";
    public static final String PERSES_PORT = "observability.perses.port";
    public static final int DEFAULT_PERSES_PORT = 3000;

    private ObservabilityProperties() {
    }
}
