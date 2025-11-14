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
package org.apache.camel.opentelemetry.metrics.routepolicy;

import org.apache.camel.Route;

import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.DEFAULT_CAMEL_ROUTE_POLICY_EXCHANGES_EXTERNAL_REDELIVERIES_METER_NAME;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.DEFAULT_CAMEL_ROUTE_POLICY_EXCHANGES_FAILED_METER_NAME;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.DEFAULT_CAMEL_ROUTE_POLICY_EXCHANGES_FAILURES_HANDLED_METER_NAME;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.DEFAULT_CAMEL_ROUTE_POLICY_EXCHANGES_SUCCEEDED_METER_NAME;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.DEFAULT_CAMEL_ROUTE_POLICY_EXCHANGES_TOTAL_METER_NAME;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.DEFAULT_CAMEL_ROUTE_POLICY_METER_NAME;

/**
 * Provides a strategy to provide metric names for OpenTelemetry route policy metrics.
 */
public interface OpenTelemetryRoutePolicyNamingStrategy {

    /**
     * Default naming strategy that uses opentelemetry naming convention.
     */
    OpenTelemetryRoutePolicyNamingStrategy DEFAULT = new OpenTelemetryRoutePolicyNamingStrategy() {
        @Override
        public String getName(Route route) {
            return DEFAULT_CAMEL_ROUTE_POLICY_METER_NAME;
        }

        @Override
        public String formatName(String name) {
            return name;
        }

        @Override
        public String getExchangesSucceededName(Route route) {
            return formatName(DEFAULT_CAMEL_ROUTE_POLICY_EXCHANGES_SUCCEEDED_METER_NAME);
        }

        @Override
        public String getExchangesFailedName(Route route) {
            return formatName(DEFAULT_CAMEL_ROUTE_POLICY_EXCHANGES_FAILED_METER_NAME);
        }

        @Override
        public String getExchangesTotalName(Route route) {
            return formatName(DEFAULT_CAMEL_ROUTE_POLICY_EXCHANGES_TOTAL_METER_NAME);
        }

        @Override
        public String getFailuresHandledName(Route route) {
            return formatName(DEFAULT_CAMEL_ROUTE_POLICY_EXCHANGES_FAILURES_HANDLED_METER_NAME);
        }

        @Override
        public String getExternalRedeliveriesName(Route route) {
            return formatName(DEFAULT_CAMEL_ROUTE_POLICY_EXCHANGES_EXTERNAL_REDELIVERIES_METER_NAME);
        }
    };

    String getName(Route route);

    String formatName(String name);

    String getExchangesSucceededName(Route route);

    String getExchangesFailedName(Route route);

    String getExchangesTotalName(Route route);

    String getFailuresHandledName(Route route);

    String getExternalRedeliveriesName(Route route);
}
