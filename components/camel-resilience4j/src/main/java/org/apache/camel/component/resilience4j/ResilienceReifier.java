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
package org.apache.camel.component.resilience4j;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.model.CircuitBreakerDefinition;
import org.apache.camel.model.Model;
import org.apache.camel.model.Resilience4jConfigurationCommon;
import org.apache.camel.model.Resilience4jConfigurationDefinition;
import org.apache.camel.reifier.ProcessorReifier;
import org.apache.camel.spi.BeanIntrospection;
import org.apache.camel.support.PropertyBindingSupport;
import org.apache.camel.util.function.Suppliers;

public class ResilienceReifier extends ProcessorReifier<CircuitBreakerDefinition> {

    public ResilienceReifier(Route route, CircuitBreakerDefinition definition) {
        super(route, definition);
    }

    @Override
    public Processor createProcessor() throws Exception {
        // create the regular and fallback processors
        Processor processor = createChildProcessor(true);
        Processor fallback = null;
        if (definition.getOnFallback() != null) {
            fallback = createProcessor(definition.getOnFallback());
        }
        boolean fallbackViaNetwork = definition.getOnFallback() != null && parseBoolean(definition.getOnFallback().getFallbackViaNetwork(), false);
        if (fallbackViaNetwork) {
            throw new UnsupportedOperationException("camel-resilience4j does not support onFallbackViaNetwork");
        }
        final Resilience4jConfigurationCommon config = buildResilience4jConfiguration();
        CircuitBreakerConfig cbConfig = configureCircuitBreaker(config);
        BulkheadConfig bhConfig = configureBulkHead(config);
        TimeLimiterConfig tlConfig = configureTimeLimiter(config);

        ResilienceProcessor answer = new ResilienceProcessor(cbConfig, bhConfig, tlConfig, processor, fallback);
        configureTimeoutExecutorService(answer, config);
        // using any existing circuit breakers?
        if (config.getCircuitBreakerRef() != null) {
            CircuitBreaker cb = mandatoryLookup(parseString(config.getCircuitBreakerRef()), CircuitBreaker.class);
            answer.setCircuitBreaker(cb);
        }
        return answer;
    }

    private CircuitBreakerConfig configureCircuitBreaker(Resilience4jConfigurationCommon config) {
        CircuitBreakerConfig.Builder builder = CircuitBreakerConfig.custom();
        if (config.getAutomaticTransitionFromOpenToHalfOpenEnabled() != null) {
            builder.automaticTransitionFromOpenToHalfOpenEnabled(parseBoolean(config.getAutomaticTransitionFromOpenToHalfOpenEnabled()));
        }
        if (config.getFailureRateThreshold() != null) {
            builder.failureRateThreshold(parseFloat(config.getFailureRateThreshold()));
        }
        if (config.getMinimumNumberOfCalls() != null) {
            builder.minimumNumberOfCalls(parseInt(config.getMinimumNumberOfCalls()));
        }
        if (config.getPermittedNumberOfCallsInHalfOpenState() != null) {
            builder.permittedNumberOfCallsInHalfOpenState(parseInt(config.getPermittedNumberOfCallsInHalfOpenState()));
        }
        if (config.getSlidingWindowSize() != null) {
            builder.slidingWindowSize(parseInt(config.getSlidingWindowSize()));
        }
        if (config.getSlidingWindowType() != null) {
            builder.slidingWindowType(CircuitBreakerConfig.SlidingWindowType.valueOf(config.getSlidingWindowType()));
        }
        if (config.getSlowCallDurationThreshold() != null) {
            builder.slowCallDurationThreshold(Duration.ofSeconds(parseLong(config.getSlowCallDurationThreshold())));
        }
        if (config.getSlowCallRateThreshold() != null) {
            builder.slowCallRateThreshold(parseFloat(config.getSlowCallRateThreshold()));
        }
        if (config.getWaitDurationInOpenState() != null) {
            builder.waitDurationInOpenState(Duration.ofSeconds(parseLong(config.getWaitDurationInOpenState())));
        }
        if (config.getWritableStackTraceEnabled() != null) {
            builder.writableStackTraceEnabled(parseBoolean(config.getWritableStackTraceEnabled()));
        }
        return builder.build();
    }

    private BulkheadConfig configureBulkHead(Resilience4jConfigurationCommon config) {
        if (!parseBoolean(config.getBulkheadEnabled(), false)) {
            return null;
        }

        BulkheadConfig.Builder builder = BulkheadConfig.custom();
        if (config.getBulkheadMaxConcurrentCalls() != null) {
            builder.maxConcurrentCalls(parseInt(config.getBulkheadMaxConcurrentCalls()));
        }
        if (config.getBulkheadMaxWaitDuration() != null) {
            builder.maxWaitDuration(Duration.ofMillis(parseLong(config.getBulkheadMaxWaitDuration())));
        }
        return builder.build();
    }

    private TimeLimiterConfig configureTimeLimiter(Resilience4jConfigurationCommon config) {
        if (!parseBoolean(config.getTimeoutEnabled(), false)) {
            return null;
        }

        TimeLimiterConfig.Builder builder = TimeLimiterConfig.custom();
        if (config.getTimeoutDuration() != null) {
            builder.timeoutDuration(Duration.ofMillis(parseLong(config.getTimeoutDuration())));
        }
        if (config.getTimeoutCancelRunningFuture() != null) {
            builder.cancelRunningFuture(parseBoolean(config.getTimeoutCancelRunningFuture()));
        }
        return builder.build();
    }

    private void configureTimeoutExecutorService(ResilienceProcessor processor, Resilience4jConfigurationCommon config) {
        if (!parseBoolean(config.getTimeoutEnabled(), false)) {
            return;
        }

        if (config.getTimeoutExecutorServiceRef() != null) {
            String ref = config.getTimeoutExecutorServiceRef();
            boolean shutdownThreadPool = false;
            ExecutorService executorService = lookup(ref, ExecutorService.class);
            if (executorService == null) {
                executorService = lookupExecutorServiceRef("CircuitBreaker", definition, ref);
                shutdownThreadPool = true;
            }
            processor.setExecutorService(executorService);
            processor.setShutdownExecutorService(shutdownThreadPool);
        }
    }

    // *******************************
    // Helpers
    // *******************************

    Resilience4jConfigurationDefinition buildResilience4jConfiguration() throws Exception {
        Map<String, Object> properties = new HashMap<>();

        // Extract properties from default configuration, the one configured on
        // camel context takes the precedence over those in the registry
        loadProperties(properties, Suppliers.firstNotNull(
            () -> camelContext.getExtension(Model.class).getResilience4jConfiguration(null),
            () -> lookup(ResilienceConstants.DEFAULT_RESILIENCE_CONFIGURATION_ID, Resilience4jConfigurationDefinition.class)));

        // Extract properties from referenced configuration, the one configured
        // on camel context takes the precedence over those in the registry
        if (definition.getConfigurationRef() != null) {
            final String ref = definition.getConfigurationRef();

            loadProperties(properties, Suppliers.firstNotNull(
                () -> camelContext.getExtension(Model.class).getResilience4jConfiguration(ref),
                () -> mandatoryLookup(ref, Resilience4jConfigurationDefinition.class)));
        }

        // Extract properties from local configuration
        loadProperties(properties, Optional.ofNullable(definition.getResilience4jConfiguration()));

        // Extract properties from definition
        BeanIntrospection beanIntrospection = camelContext.adapt(ExtendedCamelContext.class).getBeanIntrospection();
        beanIntrospection.getProperties(definition, properties, null, false);

        Resilience4jConfigurationDefinition config = new Resilience4jConfigurationDefinition();

        // Apply properties to a new configuration
        PropertyBindingSupport.bindProperties(camelContext, config, properties);

        return config;
    }

    private void loadProperties(Map<String, Object> properties, Optional<?> optional) {
        BeanIntrospection beanIntrospection = camelContext.adapt(ExtendedCamelContext.class).getBeanIntrospection();
        optional.ifPresent(bean -> beanIntrospection.getProperties(bean, properties, null, false));
    }

}
