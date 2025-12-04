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

package org.apache.camel.component.micrometer.routepolicy;

import static org.apache.camel.component.micrometer.MicrometerConstants.*;

import java.util.function.Predicate;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tags;
import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.apache.camel.component.micrometer.MicrometerUtils;

/**
 * Provides a strategy to derive a meter name and tags
 */
public interface MicrometerRoutePolicyNamingStrategy {

    Predicate<Meter.Id> ROUTE_POLICIES = id -> KIND_ROUTE.equals(id.getTag(KIND));

    /**
     * Default naming strategy that uses micrometer naming convention.
     */
    MicrometerRoutePolicyNamingStrategy DEFAULT = route -> DEFAULT_CAMEL_ROUTE_POLICY_METER_NAME;

    /**
     * Naming strategy that uses the classic/legacy naming style (camelCase)
     */
    MicrometerRoutePolicyNamingStrategy LEGACY = new MicrometerRoutePolicyNamingStrategy() {
        @Override
        public String getName(Route route) {
            return formatName(DEFAULT_CAMEL_ROUTE_POLICY_METER_NAME);
        }

        @Override
        public String formatName(String name) {
            return MicrometerUtils.legacyName(name);
        }
    };

    String getName(Route route);

    default String formatName(String name) {
        return name;
    }

    default String getExchangesSucceededName(Route route) {
        return formatName(DEFAULT_CAMEL_ROUTE_POLICY_EXCHANGES_SUCCEEDED_METER_NAME);
    }

    default String getExchangesFailedName(Route route) {
        return formatName(DEFAULT_CAMEL_ROUTE_POLICY_EXCHANGES_FAILED_METER_NAME);
    }

    default String getExchangesTotalName(Route route) {
        return formatName(DEFAULT_CAMEL_ROUTE_POLICY_EXCHANGES_TOTAL_METER_NAME);
    }

    default String getFailuresHandledName(Route route) {
        return formatName(DEFAULT_CAMEL_ROUTE_POLICY_EXCHANGES_FAILURES_HANDLED_METER_NAME);
    }

    default String getExternalRedeliveriesName(Route route) {
        return formatName(DEFAULT_CAMEL_ROUTE_POLICY_EXCHANGES_EXTERNAL_REDELIVERIES_METER_NAME);
    }

    default String getLongTaskName(Route route) {
        return formatName(DEFAULT_CAMEL_ROUTE_POLICY_LONGMETER_NAME);
    }

    default Tags getTags(Route route) {
        return Tags.of(
                CAMEL_CONTEXT_TAG,
                route.getCamelContext().getName(),
                KIND,
                KIND_ROUTE,
                ROUTE_ID_TAG,
                route.getId(),
                EVENT_TYPE_TAG,
                "route");
    }

    default Tags getTags(CamelContext camelContext) {
        return Tags.of(
                CAMEL_CONTEXT_TAG,
                camelContext.getName(),
                KIND,
                KIND_ROUTE,
                ROUTE_ID_TAG,
                "",
                EVENT_TYPE_TAG,
                "context");
    }

    default Tags getExchangeStatusTags(Route route) {
        return Tags.of(
                CAMEL_CONTEXT_TAG,
                route.getCamelContext().getName(),
                KIND,
                KIND_ROUTE,
                ROUTE_ID_TAG,
                route.getId(),
                EVENT_TYPE_TAG,
                "route");
    }

    default Tags getExchangeStatusTags(CamelContext camelContext) {
        return Tags.of(
                CAMEL_CONTEXT_TAG,
                camelContext.getName(),
                KIND,
                KIND_ROUTE,
                ROUTE_ID_TAG,
                "",
                EVENT_TYPE_TAG,
                "context");
    }
}
