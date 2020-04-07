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
package org.apache.camel.component.microprofile.faulttolerance;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

import io.smallrye.faulttolerance.core.circuit.breaker.CircuitBreaker;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.model.CircuitBreakerDefinition;
import org.apache.camel.model.FaultToleranceConfigurationCommon;
import org.apache.camel.model.FaultToleranceConfigurationDefinition;
import org.apache.camel.model.Model;
import org.apache.camel.reifier.ProcessorReifier;
import org.apache.camel.spi.BeanIntrospection;
import org.apache.camel.support.PropertyBindingSupport;
import org.apache.camel.util.function.Suppliers;

public class FaultToleranceReifier extends ProcessorReifier<CircuitBreakerDefinition> {

    public FaultToleranceReifier(Route route, CircuitBreakerDefinition definition) {
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
            throw new UnsupportedOperationException("camel-microprofile-fault-tolerance does not support onFallbackViaNetwork");
        }
        final FaultToleranceConfigurationCommon config = buildFaultToleranceConfiguration();

        FaultToleranceConfiguration configuration = new FaultToleranceConfiguration();
        configureCircuitBreaker(config, configuration);
        configureTimeLimiter(config, configuration);
        configureBulkhead(config, configuration);

        FaultToleranceProcessor answer = new FaultToleranceProcessor(configuration, processor, fallback);
        // using any existing circuit breakers?
        if (config.getCircuitBreakerRef() != null) {
            CircuitBreaker cb = mandatoryLookup(parseString(config.getCircuitBreakerRef()), CircuitBreaker.class);
            answer.setCircuitBreaker(cb);
        }
        configureBulkheadExecutorService(answer, config);
        return answer;
    }

    private void configureCircuitBreaker(FaultToleranceConfigurationCommon config, FaultToleranceConfiguration target) {
        target.setDelay(parseLong(config.getDelay(), 5));
        target.setSuccessThreshold(parseInt(config.getSuccessThreshold(), 1));
        target.setRequestVolumeThreshold(parseInt(config.getRequestVolumeThreshold(), 20));
        if (config.getFailureRatio() != null) {
            int num = parseInt(config.getFailureRatio(), 50);
            if (num < 1 || num > 100) {
                throw new IllegalArgumentException("FailureRatio must be between 1 and 100, was: " + num);
            }
            float percent = num / 100;
            target.setFailureRatio(percent);
        } else {
            target.setFailureRatio(0.5f);
        }
    }

    private void configureTimeLimiter(FaultToleranceConfigurationCommon config, FaultToleranceConfiguration target) {
        if (!parseBoolean(config.getTimeoutEnabled(), false)) {
            target.setTimeoutEnabled(false);
        } else {
            target.setTimeoutEnabled(true);
        }

        target.setTimeoutDuration(parseLong(config.getTimeoutDuration(),1000));
        target.setTimeoutPoolSize(parseInt(config.getTimeoutPoolSize(), 10));
    }

    private void configureBulkhead(FaultToleranceConfigurationCommon config, FaultToleranceConfiguration target) {
        if (!parseBoolean(config.getBulkheadEnabled(), false)) {
            return;
        }

        target.setBulkheadMaxConcurrentCalls(parseInt(config.getBulkheadMaxConcurrentCalls(), 10));
        target.setBulkheadWaitingTaskQueue(parseInt(config.getBulkheadWaitingTaskQueue(), 10));
    }

    private void configureBulkheadExecutorService(FaultToleranceProcessor processor, FaultToleranceConfigurationCommon config) {
        if (!parseBoolean(config.getBulkheadEnabled(), false)) {
            return;
        }

        if (config.getBulkheadExecutorServiceRef() != null) {
            String ref = config.getBulkheadExecutorServiceRef();
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

    FaultToleranceConfigurationDefinition buildFaultToleranceConfiguration() throws Exception {
        Map<String, Object> properties = new HashMap<>();

        // Extract properties from default configuration, the one configured on
        // camel context takes the precedence over those in the registry
        loadProperties(properties, Suppliers.firstNotNull(
             () -> camelContext.getExtension(Model.class).getFaultToleranceConfiguration(null),
            () -> lookup(FaultToleranceConstants.DEFAULT_FAULT_TOLERANCE_CONFIGURATION_ID, FaultToleranceConfigurationDefinition.class)));

        // Extract properties from referenced configuration, the one configured
        // on camel context takes the precedence over those in the registry
        if (definition.getConfigurationRef() != null) {
            final String ref = definition.getConfigurationRef();

            loadProperties(properties, Suppliers.firstNotNull(
                () -> camelContext.getExtension(Model.class).getFaultToleranceConfiguration(ref),
                () -> mandatoryLookup(ref, FaultToleranceConfigurationDefinition.class)));
        }

        // Extract properties from local configuration
        loadProperties(properties, Optional.ofNullable(definition.getFaultToleranceConfiguration()));

        // Extract properties from definition
        BeanIntrospection beanIntrospection = camelContext.adapt(ExtendedCamelContext.class).getBeanIntrospection();
        beanIntrospection.getProperties(definition, properties, null, false);

        FaultToleranceConfigurationDefinition config = new FaultToleranceConfigurationDefinition();

        // Apply properties to a new configuration
        PropertyBindingSupport.bindProperties(camelContext, config, properties);

        return config;
    }

    private void loadProperties(Map<String, Object> properties, Optional<?> optional) {
        BeanIntrospection beanIntrospection = camelContext.adapt(ExtendedCamelContext.class).getBeanIntrospection();
        optional.ifPresent(bean -> beanIntrospection.getProperties(bean, properties, null, false));
    }

}
