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

import java.util.concurrent.TimeUnit;

import io.opentelemetry.api.metrics.Meter;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.spi.UnitOfWork;

final class OpenTelemetryContextMetricsStatistics extends OpenTelemetryRoutePolicy.MetricsStatistics {

    private final boolean registerKamelets;
    private final boolean registerTemplates;

    OpenTelemetryContextMetricsStatistics(
            Meter meter,
            CamelContext camelContext,
            OpenTelemetryRoutePolicyNamingStrategy namingStrategy,
            OpenTelemetryRoutePolicyConfiguration configuration,
            boolean registerKamelets,
            boolean registerTemplates,
            TimeUnit timeUnit,
            TimeUnit longTaskTimeUnit) {
        super(meter, camelContext, null, namingStrategy, configuration, timeUnit, longTaskTimeUnit);
        this.registerKamelets = registerKamelets;
        this.registerTemplates = registerTemplates;
    }

    @Override
    public void onExchangeBegin(Exchange exchange) {
        // this metric is triggered for every route so we must only trigger on the root level,
        // otherwise this metric total counter will be incorrect. For example if an exchange is
        // routed via 3 routes we should only count this as 1 instead of 3.
        UnitOfWork uow = exchange.getUnitOfWork();
        if (uow != null) {
            int level = uow.routeStackLevel(registerTemplates, registerKamelets);
            if (level <= 1) {
                super.onExchangeBegin(exchange);
            }
        } else {
            super.onExchangeBegin(exchange);
        }
    }

    @Override
    public void onExchangeDone(Exchange exchange) {
        // this metric is triggered for every route so we must only trigger on the root level,
        // otherwise this metric total counter will be incorrect. For example if an exchange is
        // routed via 3 routes we should only count this as 1 instead of 3.
        UnitOfWork uow = exchange.getUnitOfWork();
        if (uow != null) {
            int level = uow.routeStackLevel(registerTemplates, registerKamelets);
            if (level <= 1) {
                super.onExchangeDone(exchange);
            }
        } else {
            super.onExchangeDone(exchange);
        }
    }
}
