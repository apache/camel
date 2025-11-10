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
package org.apache.camel.opentelemetry.metrics.eventnotifier;

import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.DEFAULT_CAMEL_ROUTES_ADDED;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.DEFAULT_CAMEL_ROUTES_RELOADED;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.DEFAULT_CAMEL_ROUTES_RUNNING;

public interface OpenTelemetryRouteEventNotifierNamingStrategy {

    /**
     * Default naming strategy.
     */
    OpenTelemetryRouteEventNotifierNamingStrategy DEFAULT = new OpenTelemetryRouteEventNotifierNamingStrategy() {
        @Override
        public String formatName(String name) {
            return name;
        }

        @Override
        public String getRouteAddedName() {
            return DEFAULT_CAMEL_ROUTES_ADDED;
        }

        @Override
        public String getRouteRunningName() {
            return DEFAULT_CAMEL_ROUTES_RUNNING;
        }

        @Override
        public String getRouteReloadedName() {
            return DEFAULT_CAMEL_ROUTES_RELOADED;
        }
    };

    String formatName(String name);

    String getRouteAddedName();

    String getRouteRunningName();

    String getRouteReloadedName();
}
