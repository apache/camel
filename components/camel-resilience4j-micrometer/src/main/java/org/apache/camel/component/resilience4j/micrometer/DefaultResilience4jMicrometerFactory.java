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

package org.apache.camel.component.resilience4j.micrometer;

import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedBulkheadMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedTimeLimiterMetrics;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.NonManagedService;
import org.apache.camel.component.micrometer.MicrometerConstants;
import org.apache.camel.component.micrometer.MicrometerUtils;
import org.apache.camel.spi.Registry;
import org.apache.camel.spi.Resilience4jMicrometerFactory;
import org.apache.camel.spi.annotations.JdkService;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@JdkService(Resilience4jMicrometerFactory.FACTORY)
public class DefaultResilience4jMicrometerFactory extends ServiceSupport
        implements Resilience4jMicrometerFactory, NonManagedService {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultResilience4jMicrometerFactory.class);

    private MeterRegistry meterRegistry;
    private CamelContext camelContext;
    private CircuitBreakerRegistry circuitBreakerRegistry;
    private TimeLimiterRegistry timeLimiterRegistry;
    private BulkheadRegistry bulkheadRegistry;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public void setMeterRegistry(Object meterRegistry) {
        this.meterRegistry = (MeterRegistry) meterRegistry;
    }

    public MeterRegistry getMeterRegistry() {
        return meterRegistry;
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        if (meterRegistry == null) {
            Registry camelRegistry = getCamelContext().getRegistry();
            meterRegistry =
                    MicrometerUtils.getOrCreateMeterRegistry(camelRegistry, MicrometerConstants.METRICS_REGISTRY_NAME);
        }
        circuitBreakerRegistry = CamelContextHelper.findSingleByType(camelContext, CircuitBreakerRegistry.class);
        if (circuitBreakerRegistry == null) {
            circuitBreakerRegistry = CircuitBreakerRegistry.ofDefaults();
            camelContext
                    .getCamelContextExtension()
                    .addContextPlugin(CircuitBreakerRegistry.class, circuitBreakerRegistry);
        }
        timeLimiterRegistry = CamelContextHelper.findSingleByType(camelContext, TimeLimiterRegistry.class);
        if (timeLimiterRegistry == null) {
            timeLimiterRegistry = TimeLimiterRegistry.ofDefaults();
            camelContext.getCamelContextExtension().addContextPlugin(TimeLimiterRegistry.class, timeLimiterRegistry);
        }
        bulkheadRegistry = CamelContextHelper.findSingleByType(camelContext, BulkheadRegistry.class);
        if (bulkheadRegistry == null) {
            bulkheadRegistry = BulkheadRegistry.ofDefaults();
            camelContext.getCamelContextExtension().addContextPlugin(BulkheadRegistry.class, bulkheadRegistry);
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // bind circuit breakers to micrometer metrics
        TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(circuitBreakerRegistry)
                .bindTo(meterRegistry);
        TaggedTimeLimiterMetrics.ofTimeLimiterRegistry(timeLimiterRegistry).bindTo(meterRegistry);
        TaggedBulkheadMetrics.ofBulkheadRegistry(bulkheadRegistry).bindTo(meterRegistry);

        LOG.info("Enabled Micrometer statistics with Resilience4j Circuit Breakers");
    }
}
