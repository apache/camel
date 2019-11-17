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
package org.apache.camel.example.cdi.metrics;

import java.util.concurrent.TimeUnit;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Disposes;
import javax.enterprise.inject.Produces;
import javax.inject.Singleton;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.RatioGauge.Ratio;
import com.codahale.metrics.Slf4jReporter;
import com.codahale.metrics.annotation.Metric;
import io.astefanutti.metrics.cdi.MetricsConfiguration;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.metrics.MetricsConstants;
import org.apache.camel.spi.CamelEvent.CamelContextStartedEvent;

/**
 * This example registers the following meters into the Metrics registry:
 * <ul>
 *     <li>{@code attempt}</code>: meters the number of calls
 *     made to the service to process incoming events</li>
 *     <li>{@code error}</code>: meters the number of errors
 *     corresponding to the number of events that haven't been processed</li>
 *     <li>{@code generated}</code>: meters the number of events to be processed</li>
 *     <li>{@code redelivery}</code>: meters the number of retries
 *     made to process the events</li>
 *     <li>{@code success}</code>: meters the number of events successfully processed</li>
 * </ul>
 * The invariant being: {@code attempt = redelivery - success - error}.
 * <p> In addition, a ratio gauge {@code success-ratio = success / generated} is registered.
 *
 */
class Application {

    @ApplicationScoped
    static class MetricsRoute extends RouteBuilder {

        @Override
        public void configure() {
            onException()
                .handled(true)
                .maximumRedeliveries(2)
                .logStackTrace(false)
                .logExhausted(false)
                .log(LoggingLevel.ERROR, "Failed processing ${body}")
                .to("metrics:meter:redelivery?mark=2")
                // The 'error' meter
                .to("metrics:meter:error");

            from("timer:stream?period=1000")
                .routeId("unreliable-service")
                .setBody(header(Exchange.TIMER_COUNTER).prepend("event #"))
                .log("Processing ${body}...")
                // The 'generated' meter
                .to("metrics:meter:generated")
                // The 'attempt' meter via @Metered interceptor
                .bean(UnreliableService.class)
                .filter(header(Exchange.REDELIVERED))
                    .log(LoggingLevel.WARN, "Processed ${body} after ${header.CamelRedeliveryCounter} retries")
                    .setHeader(MetricsConstants.HEADER_METER_MARK, header(Exchange.REDELIVERY_COUNTER))
                    // The 'redelivery' meter
                    .to("metrics:meter:redelivery")
                .end()
                .log("Successfully processed ${body}")
                // The 'success' meter
                .to("metrics:meter:success");
        }
    }

    @Produces
    @Metric(name = "success-ratio")
    // Register a custom gauge that's the ratio of the 'success' meter on the 'generated' meter
    Gauge<Double> successRatio(Meter success, Meter generated) {
        return () -> Ratio.of(success.getOneMinuteRate(), generated.getOneMinuteRate()).getValue();
    }

    @Produces
    @Singleton
    Slf4jReporter reporter(MetricRegistry registry) {
        return Slf4jReporter.forRegistry(registry)
            .convertRatesTo(TimeUnit.SECONDS)
            .convertDurationsTo(TimeUnit.MILLISECONDS)
            .build();
    }

    void onStart(@Observes CamelContextStartedEvent event, Slf4jReporter reporter) {
        reporter.start(10L, TimeUnit.SECONDS);
    }

    void onStop(@Disposes Slf4jReporter reporter) {
        reporter.stop();
    }

    void configure(@Observes MetricsConfiguration config) {
        // Force Metrics CDI to use absolute names
        config.useAbsoluteName(true);
    }
}
