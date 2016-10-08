/**
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
package org.apache.camel.example.metrics.cdi;

import java.util.concurrent.TimeUnit;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Produces;
import javax.inject.Named;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Slf4jReporter;
import io.astefanutti.metrics.cdi.MetricsConfiguration;
import org.apache.camel.component.metrics.MetricsComponent;
import org.apache.camel.management.event.CamelContextStartedEvent;
import org.apache.camel.management.event.CamelContextStoppedEvent;

class MetricsCdiConfig {

    @Produces
    @ApplicationScoped
    @Named(MetricsComponent.METRIC_REGISTRY_NAME)
    // TODO: remove when Camel Metrics component looks up for the Metrics registry by type only
    private MetricRegistry registry = new MetricRegistry();

    @Produces
    @ApplicationScoped
    private Slf4jReporter reporter(MetricRegistry registry) {
        return Slf4jReporter.forRegistry(registry)
            .convertRatesTo(TimeUnit.SECONDS)
            .convertDurationsTo(TimeUnit.MILLISECONDS)
            .build();
    }

    private static void onStart(@Observes CamelContextStartedEvent event, Slf4jReporter reporter) {
        reporter.start(10L, TimeUnit.SECONDS);
    }

    private static void onStop(@Observes CamelContextStoppedEvent event, Slf4jReporter reporter) {
        reporter.stop();
    }

    private static void configure(@Observes MetricsConfiguration config) {
        config.useAbsoluteName(true);
    }
}
