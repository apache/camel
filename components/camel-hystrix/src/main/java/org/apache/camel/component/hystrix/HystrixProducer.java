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
package org.apache.camel.component.hystrix;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixRequestCache;
import com.netflix.hystrix.HystrixThreadPoolKey;
import com.netflix.hystrix.HystrixThreadPoolProperties;
import com.netflix.hystrix.strategy.HystrixPlugins;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;

/**
 * The Hystrix producer.
 */
public class HystrixProducer extends DefaultProducer {
    private HystrixConfiguration configuration;
    private HystrixRequestContext requestContext;

    public HystrixProducer(HystrixEndpoint endpoint, HystrixConfiguration configuration) {
        super(endpoint);
        this.configuration = configuration;
    }

    public void process(final Exchange exchange) throws Exception {
        HystrixCommand.Setter setter = HystrixCommand.Setter.withGroupKey(
                HystrixCommandGroupKey.Factory.asKey(configuration.getGroupKey()));
        setCommandPropertiesDefaults(setter);
        setThreadPoolPropertiesDefaults(setter);

        CamelHystrixCommand camelHystrixCommand = new CamelHystrixCommand(setter, exchange, getCacheKey(exchange),
                configuration.getRunEndpointId(), configuration.getFallbackEndpointId());

        checkRequestContextPresent(exchange);
        clearCache(camelHystrixCommand.getCommandKey(), exchange);
        camelHystrixCommand.execute();
    }

    private void setCommandPropertiesDefaults(HystrixCommand.Setter setter) {
        if (configuration.getCommandKey() != null) {
            setter.andCommandKey(HystrixCommandKey.Factory.asKey(configuration.getCommandKey()));
        }

        HystrixCommandProperties.Setter commandDefaults = HystrixCommandProperties.Setter();
        setter.andCommandPropertiesDefaults(commandDefaults);

        if (configuration.getCircuitBreakerEnabled() != null) {
            commandDefaults.withCircuitBreakerEnabled(configuration.getCircuitBreakerEnabled());
        }

        if (configuration.getCircuitBreakerErrorThresholdPercentage() != null) {
            commandDefaults.withCircuitBreakerErrorThresholdPercentage(
                    configuration.getCircuitBreakerErrorThresholdPercentage());
        }

        if (configuration.getCircuitBreakerForceClosed() != null) {
            commandDefaults.withCircuitBreakerForceClosed(configuration.getCircuitBreakerForceClosed());
        }

        if (configuration.getCircuitBreakerForceOpen() != null) {
            commandDefaults.withCircuitBreakerForceOpen(configuration.getCircuitBreakerForceOpen());
        }

        if (configuration.getCircuitBreakerRequestVolumeThreshold() != null) {
            commandDefaults.withCircuitBreakerRequestVolumeThreshold(
                    configuration.getCircuitBreakerRequestVolumeThreshold());
        }

        if (configuration.getCircuitBreakerSleepWindowInMilliseconds() != null) {
            commandDefaults.withCircuitBreakerSleepWindowInMilliseconds(
                    configuration.getCircuitBreakerSleepWindowInMilliseconds());
        }

        if (configuration.getExecutionIsolationSemaphoreMaxConcurrentRequests() != null) {
            commandDefaults.withExecutionIsolationSemaphoreMaxConcurrentRequests(
                    configuration.getExecutionIsolationSemaphoreMaxConcurrentRequests());
        }

        if (configuration.getExecutionIsolationStrategy() != null) {
            commandDefaults.withExecutionIsolationStrategy(HystrixCommandProperties.ExecutionIsolationStrategy.valueOf(
                    configuration.getExecutionIsolationStrategy()));
        }

        if (configuration.getExecutionIsolationThreadInterruptOnTimeout() != null) {
            commandDefaults.withExecutionIsolationThreadInterruptOnTimeout(
                    configuration.getExecutionIsolationThreadInterruptOnTimeout());
        }

        if (configuration.getExecutionTimeoutInMilliseconds() != null) {
            commandDefaults.withExecutionTimeoutInMilliseconds(configuration.getExecutionTimeoutInMilliseconds());
        }

        if (configuration.getExecutionTimeoutEnabled() != null) {
            commandDefaults.withExecutionTimeoutEnabled(configuration.getExecutionTimeoutEnabled());
        }

        if (configuration.getFallbackIsolationSemaphoreMaxConcurrentRequests() != null) {
            commandDefaults.withFallbackIsolationSemaphoreMaxConcurrentRequests(
                    configuration.getFallbackIsolationSemaphoreMaxConcurrentRequests());
        }

        if (configuration.getFallbackEnabled() != null) {
            commandDefaults.withFallbackEnabled(configuration.getFallbackEnabled());
        }
        if (configuration.getMetricsHealthSnapshotIntervalInMilliseconds() != null) {
            commandDefaults.withMetricsHealthSnapshotIntervalInMilliseconds(configuration.getMetricsHealthSnapshotIntervalInMilliseconds());
        }

        if (configuration.getMetricsRollingPercentileBucketSize() != null) {
            commandDefaults.withMetricsRollingPercentileBucketSize(configuration.getMetricsRollingPercentileBucketSize());
        }

        if (configuration.getMetricsRollingPercentileEnabled() != null) {
            commandDefaults.withMetricsRollingPercentileEnabled(configuration.getMetricsRollingPercentileEnabled());
        }

        if (configuration.getMetricsRollingPercentileWindowInMilliseconds() != null) {
            commandDefaults.withMetricsRollingPercentileWindowInMilliseconds(configuration.getMetricsRollingPercentileWindowInMilliseconds());
        }

        if (configuration.getMetricsRollingPercentileWindowBuckets() != null) {
            commandDefaults.withMetricsRollingPercentileWindowBuckets(configuration.getMetricsRollingPercentileWindowBuckets());
        }

        if (configuration.getMetricsRollingStatisticalWindowInMilliseconds() != null) {
            commandDefaults.withMetricsRollingStatisticalWindowInMilliseconds(configuration.getMetricsRollingStatisticalWindowInMilliseconds());
        }

        if (configuration.getMetricsRollingStatisticalWindowBuckets() != null) {
            commandDefaults.withMetricsRollingStatisticalWindowBuckets(configuration.getMetricsRollingStatisticalWindowBuckets());
        }

        if (configuration.getRequestCacheEnabled() != null) {
            commandDefaults.withRequestCacheEnabled(configuration.getRequestCacheEnabled());
        }

        if (configuration.getRequestLogEnabled() != null) {
            commandDefaults.withRequestLogEnabled(configuration.getRequestLogEnabled());
        }
    }

    private void setThreadPoolPropertiesDefaults(HystrixCommand.Setter setter) {
        if (configuration.getThreadPoolKey() != null) {
            setter.andThreadPoolKey(HystrixThreadPoolKey.Factory.asKey(configuration.getThreadPoolKey()));
        }

        HystrixThreadPoolProperties.Setter threadPoolProperties = HystrixThreadPoolProperties.Setter();
        setter.andThreadPoolPropertiesDefaults(threadPoolProperties);

        if (configuration.getCoreSize() != null) {
            threadPoolProperties.withCoreSize(configuration.getCoreSize());
        }
        if (configuration.getKeepAliveTimeMinutes() != null) {
            threadPoolProperties.withKeepAliveTimeMinutes(configuration.getKeepAliveTimeMinutes());
        }
        if (configuration.getMaxQueueSize() != null) {
            threadPoolProperties.withMaxQueueSize(configuration.getMaxQueueSize());
        }
        if (configuration.getQueueSizeRejectionThreshold() != null) {
            threadPoolProperties.withQueueSizeRejectionThreshold(configuration.getQueueSizeRejectionThreshold());
        }
        if (configuration.getMetricsRollingStatisticalWindowInMilliseconds() != null) {
            threadPoolProperties.withMetricsRollingStatisticalWindowInMilliseconds(
                    configuration.getMetricsRollingStatisticalWindowInMilliseconds());
        }
        if (configuration.getMetricsRollingStatisticalWindowBuckets() != null) {
            threadPoolProperties.withMetricsRollingStatisticalWindowBuckets(
                    configuration.getMetricsRollingStatisticalWindowBuckets());
        }
    }

    private String getCacheKey(Exchange exchange) {
        return configuration.getCacheKeyExpression() != null
                ? configuration.getCacheKeyExpression().evaluate(exchange, String.class) : null;
    }

    private synchronized void checkRequestContextPresent(Exchange exchange) {
        if (!HystrixRequestContext.isCurrentThreadInitialized()) {
            HystrixRequestContext customRequestContext = exchange.getIn()
                    .getHeader(HystrixConstants.CAMEL_HYSTRIX_REQUEST_CONTEXT_KEY, HystrixRequestContext.class);

            if (customRequestContext != null) {
                HystrixRequestContext.setContextOnCurrentThread(customRequestContext);
            } else {
                HystrixRequestContext.setContextOnCurrentThread(requestContext);
                exchange.getIn().setHeader(HystrixConstants.CAMEL_HYSTRIX_REQUEST_CONTEXT_KEY, requestContext);
            }
        }
    }

    private void clearCache(HystrixCommandKey camelHystrixCommand, Exchange exchange) {
        Boolean clearCache = exchange.getIn().getHeader(HystrixConstants.CAMEL_HYSTRIX_CLEAR_CACHE_FIRST, Boolean.class);
        if (clearCache != null && clearCache) {
            HystrixRequestCache.getInstance(camelHystrixCommand,
                    HystrixPlugins.getInstance().getConcurrencyStrategy()).clear(String.valueOf(getCacheKey(exchange)));
        }
    }

    @Override
    protected void doStart() throws Exception {
        if (configuration.getPropagateRequestContext() != null && configuration.getPropagateRequestContext()) {
            requestContext = HystrixRequestContext.initializeContext();
        }
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        if (requestContext != null) {
            requestContext.shutdown();
        }
        super.doStop();
    }

}
