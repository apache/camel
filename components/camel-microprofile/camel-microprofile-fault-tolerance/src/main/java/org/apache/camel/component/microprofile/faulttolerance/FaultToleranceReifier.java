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
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.model.CircuitBreakerDefinition;
import org.apache.camel.model.FaultToleranceConfigurationCommon;
import org.apache.camel.model.FaultToleranceConfigurationDefinition;
import org.apache.camel.model.Model;
import org.apache.camel.reifier.ProcessorReifier;
import org.apache.camel.spi.BeanIntrospection;
import org.apache.camel.spi.ExtendedPropertyConfigurerGetter;
import org.apache.camel.spi.PropertyConfigurer;
import org.apache.camel.support.PluginHelper;
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
        boolean fallbackViaNetwork
                = definition.getOnFallback() != null && parseBoolean(definition.getOnFallback().getFallbackViaNetwork(), false);
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
        if (config.getCircuitBreaker() != null) {
            CircuitBreaker<?> cb = mandatoryLookup(parseString(config.getCircuitBreaker()), CircuitBreaker.class);
            answer.setCircuitBreaker(cb);
        }
        configureBulkheadExecutorService(answer, config);
        return answer;
    }

    private void configureCircuitBreaker(FaultToleranceConfigurationCommon config, FaultToleranceConfiguration target) {
        target.setDelay(parseDuration(config.getDelay(), 5000));
        target.setSuccessThreshold(parseInt(config.getSuccessThreshold(), 1));
        target.setRequestVolumeThreshold(parseInt(config.getRequestVolumeThreshold(), 20));
        if (config.getFailureRatio() != null) {
            float num = parseFloat(config.getFailureRatio(), 50);
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

        target.setTimeoutDuration(parseDuration(config.getTimeoutDuration(), 1000));
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

        if (config.getBulkheadExecutorService() != null) {
            String ref = config.getBulkheadExecutorService();
            boolean shutdownThreadPool = false;
            ExecutorService executorService = lookupByNameAndType(ref, ExecutorService.class);
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

        final PropertyConfigurer configurer = PluginHelper.getConfigurerResolver(camelContext)
                .resolvePropertyConfigurer(FaultToleranceConfigurationDefinition.class.getName(), camelContext);

        // Extract properties from default configuration, the one configured on
        // camel context takes the precedence over those in the registry
        loadProperties(properties, Suppliers.firstNotNull(
                () -> camelContext.getCamelContextExtension().getContextPlugin(Model.class)
                        .getFaultToleranceConfiguration(null),
                () -> lookupByNameAndType(FaultToleranceConstants.DEFAULT_FAULT_TOLERANCE_CONFIGURATION_ID,
                        FaultToleranceConfigurationDefinition.class)),
                configurer);

        // Extract properties from referenced configuration, the one configured
        // on camel context takes the precedence over those in the registry
        if (definition.getConfiguration() != null) {
            final String ref = parseString(definition.getConfiguration());

            loadProperties(properties, Suppliers.firstNotNull(
                    () -> camelContext.getCamelContextExtension().getContextPlugin(Model.class)
                            .getFaultToleranceConfiguration(ref),
                    () -> mandatoryLookup(ref, FaultToleranceConfigurationDefinition.class)),
                    configurer);
        }

        // Extract properties from local configuration
        loadProperties(properties, Optional.ofNullable(definition.getFaultToleranceConfiguration()), configurer);

        // Apply properties to a new configuration
        FaultToleranceConfigurationDefinition config = new FaultToleranceConfigurationDefinition();
        PropertyBindingSupport.build()
                .withCamelContext(camelContext)
                .withConfigurer(configurer)
                .withProperties(properties)
                .withTarget(config)
                .bind();

        return config;
    }

    private void loadProperties(Map<String, Object> properties, Optional<?> optional, PropertyConfigurer configurer) {
        BeanIntrospection beanIntrospection = PluginHelper.getBeanIntrospection(camelContext);
        optional.ifPresent(bean -> {
            if (configurer instanceof ExtendedPropertyConfigurerGetter) {
                ExtendedPropertyConfigurerGetter getter = (ExtendedPropertyConfigurerGetter) configurer;
                Map<String, Object> types = getter.getAllOptions(bean);
                types.forEach((k, t) -> {
                    Object value = getter.getOptionValue(bean, k, true);
                    if (value != null) {
                        properties.put(k, value);
                    }
                });
            } else {
                // no configurer found so use bean introspection (reflection)
                beanIntrospection.getProperties(bean, properties, null, false);
            }
        });
    }

}
