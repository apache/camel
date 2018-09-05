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
import org.apache.camel.Processor;
import org.apache.camel.impl.TypedProcessorFactory;
import org.apache.camel.model.HystrixConfigurationDefinition;
import org.apache.camel.model.HystrixDefinition;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.function.Suppliers;

import static org.apache.camel.util.CamelContextHelper.lookup;
import static org.apache.camel.util.CamelContextHelper.mandatoryLookup;

/**
 * To integrate camel-hystrix with the Camel routes using the Hystrix EIP.
 */
public class HystrixProcessorFactory extends TypedProcessorFactory<HystrixDefinition> {

    public HystrixProcessorFactory() {
        super(HystrixDefinition.class);
    }

    @Override
    public Processor doCreateProcessor(RouteContext routeContext, HystrixDefinition definition) throws Exception {
        // create the regular and fallback processors
        Processor processor = definition.createChildProcessor(routeContext, true);
        Processor fallback = null;
        if (definition.getOnFallback() != null) {
            fallback = definition.getOnFallback().createProcessor(routeContext);
        }

        final HystrixConfigurationDefinition config = buildHystrixConfiguration(routeContext.getCamelContext(), definition);
        final String id = definition.idOrCreate(routeContext.getCamelContext().getNodeIdFactory());

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
        boolean fallbackViaNetwork = definition.getOnFallback() != null && definition.getOnFallback().isFallbackViaNetwork();
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
            command.withCircuitBreakerEnabled(config.getCircuitBreakerEnabled());
        }
        if (config.getCircuitBreakerErrorThresholdPercentage() != null) {
            command.withCircuitBreakerErrorThresholdPercentage(config.getCircuitBreakerErrorThresholdPercentage());
        }
        if (config.getCircuitBreakerForceClosed() != null) {
            command.withCircuitBreakerForceClosed(config.getCircuitBreakerForceClosed());
        }
        if (config.getCircuitBreakerForceOpen() != null) {
            command.withCircuitBreakerForceOpen(config.getCircuitBreakerForceOpen());
        }
        if (config.getCircuitBreakerRequestVolumeThreshold() != null) {
            command.withCircuitBreakerRequestVolumeThreshold(config.getCircuitBreakerRequestVolumeThreshold());
        }
        if (config.getCircuitBreakerSleepWindowInMilliseconds() != null) {
            command.withCircuitBreakerSleepWindowInMilliseconds(config.getCircuitBreakerSleepWindowInMilliseconds());
        }
        if (config.getExecutionIsolationSemaphoreMaxConcurrentRequests() != null) {
            command.withExecutionIsolationSemaphoreMaxConcurrentRequests(config.getExecutionIsolationSemaphoreMaxConcurrentRequests());
        }
        if (config.getExecutionIsolationStrategy() != null) {
            command.withExecutionIsolationStrategy(HystrixCommandProperties.ExecutionIsolationStrategy.valueOf(config.getExecutionIsolationStrategy()));
        }
        if (config.getExecutionIsolationThreadInterruptOnTimeout() != null) {
            command.withExecutionIsolationThreadInterruptOnTimeout(config.getExecutionIsolationThreadInterruptOnTimeout());
        }
        if (config.getExecutionTimeoutInMilliseconds() != null) {
            command.withExecutionTimeoutInMilliseconds(config.getExecutionTimeoutInMilliseconds());
        }
        if (config.getExecutionTimeoutEnabled() != null) {
            command.withExecutionTimeoutEnabled(config.getExecutionTimeoutEnabled());
        }
        if (config.getFallbackIsolationSemaphoreMaxConcurrentRequests() != null) {
            command.withFallbackIsolationSemaphoreMaxConcurrentRequests(config.getFallbackIsolationSemaphoreMaxConcurrentRequests());
        }
        if (config.getFallbackEnabled() != null) {
            command.withFallbackEnabled(config.getFallbackEnabled());
        }
        if (config.getMetricsHealthSnapshotIntervalInMilliseconds() != null) {
            command.withMetricsHealthSnapshotIntervalInMilliseconds(config.getMetricsHealthSnapshotIntervalInMilliseconds());
        }
        if (config.getMetricsRollingPercentileBucketSize() != null) {
            command.withMetricsRollingPercentileBucketSize(config.getMetricsRollingPercentileBucketSize());
        }
        if (config.getMetricsRollingPercentileEnabled() != null) {
            command.withMetricsRollingPercentileEnabled(config.getMetricsRollingPercentileEnabled());
        }
        if (config.getMetricsRollingPercentileWindowInMilliseconds() != null) {
            command.withMetricsRollingPercentileWindowInMilliseconds(config.getMetricsRollingPercentileWindowInMilliseconds());
        }
        if (config.getMetricsRollingPercentileWindowBuckets() != null) {
            command.withMetricsRollingPercentileWindowBuckets(config.getMetricsRollingPercentileWindowBuckets());
        }
        if (config.getMetricsRollingStatisticalWindowInMilliseconds() != null) {
            command.withMetricsRollingStatisticalWindowInMilliseconds(config.getMetricsRollingStatisticalWindowInMilliseconds());
        }
        if (config.getMetricsRollingStatisticalWindowBuckets() != null) {
            command.withMetricsRollingStatisticalWindowBuckets(config.getMetricsRollingStatisticalWindowBuckets());
        }
        if (config.getRequestLogEnabled() != null) {
            command.withRequestLogEnabled(config.getRequestLogEnabled());
        }
        if (config.getCorePoolSize() != null) {
            threadPool.withCoreSize(config.getCorePoolSize());
        }
        if (config.getMaximumSize() != null) {
            threadPool.withMaximumSize(config.getMaximumSize());
        }
        if (config.getKeepAliveTime() != null) {
            threadPool.withKeepAliveTimeMinutes(config.getKeepAliveTime());
        }
        if (config.getMaxQueueSize() != null) {
            threadPool.withMaxQueueSize(config.getMaxQueueSize());
        }
        if (config.getQueueSizeRejectionThreshold() != null) {
            threadPool.withQueueSizeRejectionThreshold(config.getQueueSizeRejectionThreshold());
        }
        if (config.getThreadPoolRollingNumberStatisticalWindowInMilliseconds() != null) {
            threadPool.withMetricsRollingStatisticalWindowInMilliseconds(config.getThreadPoolRollingNumberStatisticalWindowInMilliseconds());
        }
        if (config.getThreadPoolRollingNumberStatisticalWindowBuckets() != null) {
            threadPool.withMetricsRollingStatisticalWindowBuckets(config.getThreadPoolRollingNumberStatisticalWindowBuckets());
        }
        if (config.getAllowMaximumSizeToDivergeFromCoreSize() != null) {
            threadPool.withAllowMaximumSizeToDivergeFromCoreSize(config.getAllowMaximumSizeToDivergeFromCoreSize());
        }
    }

    // *******************************
    // Helpers
    // *******************************

    HystrixConfigurationDefinition buildHystrixConfiguration(CamelContext camelContext, HystrixDefinition definition) throws Exception {
        Map<String, Object> properties = new HashMap<>();

        // Extract properties from default configuration, the one configured on
        // camel context takes the precedence over those in the registry
        loadProperties(properties, Suppliers.firstNotNull(
            () -> camelContext.getHystrixConfiguration(null),
            () -> lookup(camelContext, HystrixConstants.DEFAULT_HYSTRIX_CONFIGURATION_ID, HystrixConfigurationDefinition.class))
        );

        // Extract properties from referenced configuration, the one configured
        // on camel context takes the precedence over those in the registry
        if (definition.getHystrixConfigurationRef() != null) {
            final String ref = definition.getHystrixConfigurationRef();

            loadProperties(properties, Suppliers.firstNotNull(
                () -> camelContext.getHystrixConfiguration(ref),
                () -> mandatoryLookup(camelContext, ref, HystrixConfigurationDefinition.class))
            );
        }

        // Extract properties from local configuration
        loadProperties(properties, Optional.ofNullable(definition.getHystrixConfiguration()));

        // Extract properties from definition
        IntrospectionSupport.getProperties(definition, properties, null, false);

        HystrixConfigurationDefinition config = new HystrixConfigurationDefinition();

        // Apply properties to a new configuration
        IntrospectionSupport.setProperties(camelContext, camelContext.getTypeConverter(), config, properties);

        return config;
    }

    private void loadProperties(Map<String, Object> properties, Optional<?> optional) {
        optional.ifPresent(bean -> IntrospectionSupport.getProperties(bean, properties, null, false));
    }
}
