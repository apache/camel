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
package org.apache.camel.example.micrometer;

import java.time.Duration;

import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.component.micrometer.DistributionStatisticConfigFilter;
import org.apache.camel.component.micrometer.MicrometerConstants;
import org.apache.camel.component.micrometer.messagehistory.MicrometerMessageHistoryFactory;
import org.apache.camel.component.micrometer.routepolicy.MicrometerRoutePolicyFactory;
import org.apache.camel.spring.javaconfig.CamelConfiguration;
import org.apache.camel.spring.javaconfig.Main;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import static org.apache.camel.component.micrometer.MicrometerConstants.DISTRIBUTION_SUMMARIES;
import static org.apache.camel.component.micrometer.messagehistory.MicrometerMessageHistoryNamingStrategy.MESSAGE_HISTORIES;
import static org.apache.camel.component.micrometer.routepolicy.MicrometerRoutePolicyNamingStrategy.ROUTE_POLICIES;

//START SNIPPET: CamelPrometheusExample

@Configuration
@ComponentScan
public class CamelPrometheusExample extends CamelConfiguration {

    /**
     * Allow this route to be run as an application
     */
    public static void main(String[] args) throws Exception {
        Main main = new Main();
        main.setConfigClass(CamelPrometheusExample.class);
        main.run();
    }

    @Override
    protected void setupCamelContext(CamelContext camelContext) {
        camelContext.addRoutePolicyFactory(new MicrometerRoutePolicyFactory());
        camelContext.setMessageHistoryFactory(new MicrometerMessageHistoryFactory());
    }

    /**
     * Set up registry. When using Spring Boot, this is provided for you, and you have to provide
     * a bean of type MeterRegistryCustomizer in order to apply common tags, filters, etc.
     */
    @Bean(name = MicrometerConstants.METRICS_REGISTRY_NAME)
    public PrometheusMeterRegistry meterRegistry() {

        PrometheusMeterRegistry meterRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

        DistributionStatisticConfigFilter timerMeterFilter = new DistributionStatisticConfigFilter()
                .andAppliesTo(ROUTE_POLICIES)
                .orAppliesTo(MESSAGE_HISTORIES)
                .setPublishPercentileHistogram(true)
                .setMinimumExpectedDuration(Duration.ofMillis(1L))
                .setMaximumExpectedDuration(Duration.ofMillis(150L));

        DistributionStatisticConfigFilter summaryMeterFilter = new DistributionStatisticConfigFilter()
                .andAppliesTo(DISTRIBUTION_SUMMARIES)
                .setPublishPercentileHistogram(true)
                .setMinimumExpectedValue(1L)
                .setMaximumExpectedValue(100L);

        meterRegistry.config()
                // Application name. Required by Grafana JVM Dashboard 4701.
                .commonTags(Tags.of("application", "CamelPrometheusExample"))
                .meterFilter(timerMeterFilter)
                .meterFilter(summaryMeterFilter);

        // Add JVM metrics. Using Spring Boot, these are added automatically

        new ClassLoaderMetrics().bindTo(meterRegistry);
        new JvmMemoryMetrics().bindTo(meterRegistry);
        new JvmGcMetrics().bindTo(meterRegistry);
        new ProcessorMetrics().bindTo(meterRegistry);
        new JvmThreadMetrics().bindTo(meterRegistry);

        return meterRegistry;
    }

}
//END SNIPPET: CamelPrometheusExample

