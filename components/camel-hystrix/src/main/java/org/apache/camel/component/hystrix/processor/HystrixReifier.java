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
package org.apache.camel.component.hystrix.processor;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixThreadPoolKey;
import com.netflix.hystrix.HystrixThreadPoolProperties;
import org.apache.camel.CamelContext;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.model.CircuitBreakerDefinition;
import org.apache.camel.model.HystrixConfigurationDefinition;
import org.apache.camel.model.Model;
import org.apache.camel.reifier.ProcessorReifier;
import org.apache.camel.spi.BeanIntrospection;
import org.apache.camel.support.PropertyBindingSupport;
import org.apache.camel.util.function.Suppliers;

public class HystrixReifier extends ProcessorReifier<CircuitBreakerDefinition> {

    public HystrixReifier(Route route, CircuitBreakerDefinition definition) {
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

        final HystrixConfigurationDefinition config = buildHystrixConfiguration();
        final String id = getId(definition);

        // group and thread pool keys to use they can be configured on configRef and config, so look there first, and if none then use default
        String groupKey = config.getGroupKey();
        String threadPoolKey = config.getThreadPoolKey();

        if (groupKey == null) {
            groupKey = HystrixConfigurationDefinition.DEFAULT_GROUP_KEY;
        }
        if (threadPoolKey == null) {
            // by default use the thread pool from the group
            threadPoolKey = groupKey;
        }

        // use the node id as the command key
        HystrixCommandKey hcCommandKey = HystrixCommandKey.Factory.asKey(id);
        HystrixCommandKey hcFallbackCommandKey = HystrixCommandKey.Factory.asKey(id + "-fallback");

        // use the configured group key
        HystrixCommandGroupKey hcGroupKey = HystrixCommandGroupKey.Factory.asKey(groupKey);
        HystrixThreadPoolKey tpKey = HystrixThreadPoolKey.Factory.asKey(threadPoolKey);

        // create setter using the default options
        HystrixCommand.Setter setter = HystrixCommand.Setter.withGroupKey(hcGroupKey)
            .andCommandKey(hcCommandKey)
            .andThreadPoolKey(tpKey);

        HystrixCommandProperties.Setter commandSetter = HystrixCommandProperties.Setter();
        setter.andCommandPropertiesDefaults(commandSetter);

        HystrixThreadPoolProperties.Setter threadPoolSetter = HystrixThreadPoolProperties.Setter();
        setter.andThreadPoolPropertiesDefaults(threadPoolSetter);

        configureHystrix(commandSetter, threadPoolSetter, config);

        // create setter for fallback via network
        HystrixCommand.Setter fallbackSetter = null;
        boolean fallbackViaNetwork = definition.getOnFallback() != null && parseBoolean(definition.getOnFallback().getFallbackViaNetwork(), false);
        if (fallbackViaNetwork) {
            // use a different thread pool that is for fallback (should never use the same thread pool as the regular command)
            HystrixThreadPoolKey tpFallbackKey = HystrixThreadPoolKey.Factory.asKey(threadPoolKey + "-fallback");

            fallbackSetter = HystrixCommand.Setter.withGroupKey(hcGroupKey)
                .andCommandKey(hcFallbackCommandKey)
                .andThreadPoolKey(tpFallbackKey);

            HystrixCommandProperties.Setter commandFallbackSetter = HystrixCommandProperties.Setter();
            fallbackSetter.andCommandPropertiesDefaults(commandFallbackSetter);

            HystrixThreadPoolProperties.Setter fallbackThreadPoolSetter = HystrixThreadPoolProperties.Setter();
            fallbackSetter.andThreadPoolPropertiesDefaults(fallbackThreadPoolSetter);

            // at first configure any shared options
            configureHystrix(commandFallbackSetter, fallbackThreadPoolSetter, config);
        }

        return new HystrixProcessor(hcGroupKey, hcCommandKey, hcFallbackCommandKey, setter, fallbackSetter, processor, fallback, fallbackViaNetwork);
    }

    private void configureHystrix(HystrixCommandProperties.Setter command, HystrixThreadPoolProperties.Setter threadPool, HystrixConfigurationDefinition config) {
        // command
        if (config.getCircuitBreakerEnabled() != null) {
            command.withCircuitBreakerEnabled(parseBoolean(config.getCircuitBreakerEnabled()));
        }
        if (config.getCircuitBreakerErrorThresholdPercentage() != null) {
            command.withCircuitBreakerErrorThresholdPercentage(parseInt(config.getCircuitBreakerErrorThresholdPercentage()));
        }
        if (config.getCircuitBreakerForceClosed() != null) {
            command.withCircuitBreakerForceClosed(parseBoolean(config.getCircuitBreakerForceClosed()));
        }
        if (config.getCircuitBreakerForceOpen() != null) {
            command.withCircuitBreakerForceOpen(parseBoolean(config.getCircuitBreakerForceOpen()));
        }
        if (config.getCircuitBreakerRequestVolumeThreshold() != null) {
            command.withCircuitBreakerRequestVolumeThreshold(parseInt(config.getCircuitBreakerRequestVolumeThreshold()));
        }
        if (config.getCircuitBreakerSleepWindowInMilliseconds() != null) {
            command.withCircuitBreakerSleepWindowInMilliseconds(parseInt(config.getCircuitBreakerSleepWindowInMilliseconds()));
        }
        if (config.getExecutionIsolationSemaphoreMaxConcurrentRequests() != null) {
            command.withExecutionIsolationSemaphoreMaxConcurrentRequests(parseInt(config.getExecutionIsolationSemaphoreMaxConcurrentRequests()));
        }
        if (config.getExecutionIsolationStrategy() != null) {
            command.withExecutionIsolationStrategy(parse(HystrixCommandProperties.ExecutionIsolationStrategy.class, config.getExecutionIsolationStrategy()));
        }
        if (config.getExecutionIsolationThreadInterruptOnTimeout() != null) {
            command.withExecutionIsolationThreadInterruptOnTimeout(parseBoolean(config.getExecutionIsolationThreadInterruptOnTimeout()));
        }
        if (config.getExecutionTimeoutInMilliseconds() != null) {
            command.withExecutionTimeoutInMilliseconds(parseInt(config.getExecutionTimeoutInMilliseconds()));
        }
        if (config.getExecutionTimeoutEnabled() != null) {
            command.withExecutionTimeoutEnabled(parseBoolean(config.getExecutionTimeoutEnabled()));
        }
        if (config.getFallbackIsolationSemaphoreMaxConcurrentRequests() != null) {
            command.withFallbackIsolationSemaphoreMaxConcurrentRequests(parseInt(config.getFallbackIsolationSemaphoreMaxConcurrentRequests()));
        }
        if (config.getFallbackEnabled() != null) {
            command.withFallbackEnabled(parseBoolean(config.getFallbackEnabled()));
        }
        if (config.getMetricsHealthSnapshotIntervalInMilliseconds() != null) {
            command.withMetricsHealthSnapshotIntervalInMilliseconds(parseInt(config.getMetricsHealthSnapshotIntervalInMilliseconds()));
        }
        if (config.getMetricsRollingPercentileBucketSize() != null) {
            command.withMetricsRollingPercentileBucketSize(parseInt(config.getMetricsRollingPercentileBucketSize()));
        }
        if (config.getMetricsRollingPercentileEnabled() != null) {
            command.withMetricsRollingPercentileEnabled(parseBoolean(config.getMetricsRollingPercentileEnabled()));
        }
        if (config.getMetricsRollingPercentileWindowInMilliseconds() != null) {
            command.withMetricsRollingPercentileWindowInMilliseconds(parseInt(config.getMetricsRollingPercentileWindowInMilliseconds()));
        }
        if (config.getMetricsRollingPercentileWindowBuckets() != null) {
            command.withMetricsRollingPercentileWindowBuckets(parseInt(config.getMetricsRollingPercentileWindowBuckets()));
        }
        if (config.getMetricsRollingStatisticalWindowInMilliseconds() != null) {
            command.withMetricsRollingStatisticalWindowInMilliseconds(parseInt(config.getMetricsRollingStatisticalWindowInMilliseconds()));
        }
        if (config.getMetricsRollingStatisticalWindowBuckets() != null) {
            command.withMetricsRollingStatisticalWindowBuckets(parseInt(config.getMetricsRollingStatisticalWindowBuckets()));
        }
        if (config.getRequestLogEnabled() != null) {
            command.withRequestLogEnabled(parseBoolean(config.getRequestLogEnabled()));
        }
        if (config.getCorePoolSize() != null) {
            threadPool.withCoreSize(parseInt(config.getCorePoolSize()));
        }
        if (config.getMaximumSize() != null) {
            threadPool.withMaximumSize(parseInt(config.getMaximumSize()));
        }
        if (config.getKeepAliveTime() != null) {
            threadPool.withKeepAliveTimeMinutes(parseInt(config.getKeepAliveTime()));
        }
        if (config.getMaxQueueSize() != null) {
            threadPool.withMaxQueueSize(parseInt(config.getMaxQueueSize()));
        }
        if (config.getQueueSizeRejectionThreshold() != null) {
            threadPool.withQueueSizeRejectionThreshold(parseInt(config.getQueueSizeRejectionThreshold()));
        }
        if (config.getThreadPoolRollingNumberStatisticalWindowInMilliseconds() != null) {
            threadPool.withMetricsRollingStatisticalWindowInMilliseconds(parseInt(config.getThreadPoolRollingNumberStatisticalWindowInMilliseconds()));
        }
        if (config.getThreadPoolRollingNumberStatisticalWindowBuckets() != null) {
            threadPool.withMetricsRollingStatisticalWindowBuckets(parseInt(config.getThreadPoolRollingNumberStatisticalWindowBuckets()));
        }
        if (config.getAllowMaximumSizeToDivergeFromCoreSize() != null) {
            threadPool.withAllowMaximumSizeToDivergeFromCoreSize(parseBoolean(config.getAllowMaximumSizeToDivergeFromCoreSize()));
        }
    }

    // *******************************
    // Helpers
    // *******************************

    HystrixConfigurationDefinition buildHystrixConfiguration() throws Exception {
        Map<String, Object> properties = new HashMap<>();

        // Extract properties from default configuration, the one configured on
        // camel context takes the precedence over those in the registry
        loadProperties(camelContext, properties, Suppliers.firstNotNull(
            () -> camelContext.getExtension(Model.class).getHystrixConfiguration(null),
            () -> lookup(HystrixConstants.DEFAULT_HYSTRIX_CONFIGURATION_ID, HystrixConfigurationDefinition.class))
        );

        // Extract properties from referenced configuration, the one configured
        // on camel context takes the precedence over those in the registry
        if (definition.getConfigurationRef() != null) {
            final String ref = parseString(definition.getConfigurationRef());

            loadProperties(camelContext, properties, Suppliers.firstNotNull(
                () -> camelContext.getExtension(Model.class).getHystrixConfiguration(ref),
                () -> mandatoryLookup(ref, HystrixConfigurationDefinition.class))
            );
        }

        // Extract properties from local configuration
        loadProperties(camelContext, properties, Optional.ofNullable(definition.getHystrixConfiguration()));

        // Extract properties from definition
        BeanIntrospection beanIntrospection = camelContext.adapt(ExtendedCamelContext.class).getBeanIntrospection();
        beanIntrospection.getProperties(definition, properties, null, false);

        HystrixConfigurationDefinition config = new HystrixConfigurationDefinition();

        // Apply properties to a new configuration
        PropertyBindingSupport.bindProperties(camelContext, config, properties);

        return config;
    }

    private void loadProperties(CamelContext camelContext, Map<String, Object> properties, Optional<?> optional) {
        BeanIntrospection beanIntrospection = camelContext.adapt(ExtendedCamelContext.class).getBeanIntrospection();
        optional.ifPresent(bean -> beanIntrospection.getProperties(bean, properties, null, false));
    }

}
