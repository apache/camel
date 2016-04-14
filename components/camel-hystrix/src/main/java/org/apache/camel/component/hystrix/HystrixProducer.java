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
import com.netflix.hystrix.HystrixCommandMetrics;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixRequestCache;
import com.netflix.hystrix.HystrixThreadPoolKey;
import com.netflix.hystrix.HystrixThreadPoolMetrics;
import com.netflix.hystrix.HystrixThreadPoolProperties;
import com.netflix.hystrix.strategy.HystrixPlugins;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.impl.ProducerCache;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.EndpointHelper;
import org.apache.camel.util.ServiceHelper;

/**
 * The Hystrix producer.
 */
public class HystrixProducer extends DefaultProducer {
    private HystrixConfiguration configuration;
    private HystrixRequestContext requestContext;
    private ProducerCache producerCache;

    public HystrixProducer(HystrixEndpoint endpoint, HystrixConfiguration configuration) {
        super(endpoint);
        this.configuration = configuration;
    }

    public void process(final Exchange exchange) throws Exception {
        HystrixCommand.Setter setter = HystrixCommand.Setter.withGroupKey(
                HystrixCommandGroupKey.Factory.asKey(exchange.getIn().getHeader(HystrixConstants.CAMEL_HYSTRIX_GROUP_KEY, configuration.getGroupKey(), String.class)));

        setCommandPropertiesDefaults(setter, exchange);
        setThreadPoolPropertiesDefaults(setter, exchange);

        // lookup the endpoints to use, which can be overridden from headers
        String run = exchange.getIn().getHeader(HystrixConstants.CAMEL_HYSTRIX_RUN_ENDPOINT, configuration.getRunEndpoint(), String.class);
        String fallback = exchange.getIn().getHeader(HystrixConstants.CAMEL_HYSTRIX_FALLBACK_ENDPOINT, configuration.getFallbackEndpoint(), String.class);
        Endpoint runEndpoint = exchange.getContext().getEndpoint(run);
        Endpoint fallbackEndpoint = fallback != null ? exchange.getContext().getEndpoint(fallback) : null;

        if (log.isDebugEnabled()) {
            log.debug("Run endpoint: {}", runEndpoint);
            log.debug("Fallback endpoint: {}", fallbackEndpoint);
        }

        CamelHystrixCommand camelHystrixCommand = new CamelHystrixCommand(setter, exchange, getCacheKey(exchange), producerCache, runEndpoint, fallbackEndpoint);

        checkRequestContextPresent(exchange);
        clearCache(camelHystrixCommand.getCommandKey(), exchange);
        camelHystrixCommand.execute();

        if (configuration.isMetricsEnabled()) {
            populateWithMetrics(exchange, camelHystrixCommand);
        }
    }

    private void setCommandPropertiesDefaults(HystrixCommand.Setter setter, Exchange exchange) {
        HystrixCommandProperties.Setter commandDefaults = HystrixCommandProperties.Setter();
        setter.andCommandPropertiesDefaults(commandDefaults);

        if (exchange.getIn().getHeader(HystrixConstants.CAMEL_HYSTRIX_COMMAND_KEY, configuration.getCommandKey(), String.class) != null) {
            setter.andCommandKey(HystrixCommandKey.Factory.asKey(
                    exchange.getIn().getHeader(HystrixConstants.CAMEL_HYSTRIX_COMMAND_KEY, configuration.getCommandKey(), String.class)));
        }

        if (exchange.getIn().getHeader(HystrixConstants.CAMEL_HYSTRIX_CIRCUIT_BREAKER_ENABLED, configuration.getCircuitBreakerEnabled(), Boolean.class) != null) {
            commandDefaults.withCircuitBreakerEnabled(
                    exchange.getIn().getHeader(HystrixConstants.CAMEL_HYSTRIX_CIRCUIT_BREAKER_ENABLED, configuration.getCircuitBreakerEnabled(), Boolean.class));
        }

        if (exchange.getIn().getHeader(HystrixConstants.CAMEL_HYSTRIX_CIRCUIT_BREAKER_ERROR_THRESHOLD_PERCENTAGE,
                configuration.getCircuitBreakerErrorThresholdPercentage(), Integer.class) != null) {
            commandDefaults.withCircuitBreakerErrorThresholdPercentage(
                    exchange.getIn().getHeader(HystrixConstants.CAMEL_HYSTRIX_CIRCUIT_BREAKER_ERROR_THRESHOLD_PERCENTAGE,
                            configuration.getCircuitBreakerErrorThresholdPercentage(), Integer.class));
        }

        if (exchange.getIn().getHeader(HystrixConstants.CAMEL_HYSTRIX_CIRCUIT_BREAKER_FORCE_CLOSED,
                configuration.getCircuitBreakerForceClosed(), Boolean.class) != null) {
            commandDefaults.withCircuitBreakerForceClosed(
                    exchange.getIn().getHeader(HystrixConstants.CAMEL_HYSTRIX_CIRCUIT_BREAKER_FORCE_CLOSED,
                            configuration.getCircuitBreakerForceClosed(), Boolean.class));
        }

        if (exchange.getIn().getHeader(HystrixConstants.CAMEL_HYSTRIX_CIRCUIT_BREAKER_FORCE_OPEN,
                configuration.getCircuitBreakerForceOpen(), Boolean.class) != null) {
            commandDefaults.withCircuitBreakerForceOpen(
                    exchange.getIn().getHeader(HystrixConstants.CAMEL_HYSTRIX_CIRCUIT_BREAKER_FORCE_OPEN,
                            configuration.getCircuitBreakerForceOpen(), Boolean.class));
        }

        if (exchange.getIn().getHeader(HystrixConstants.CAMEL_HYSTRIX_CIRCUIT_BREAKER_REQUEST_VOLUME_THRESHOLD,
                configuration.getCircuitBreakerRequestVolumeThreshold(), Integer.class) != null) {
            commandDefaults.withCircuitBreakerRequestVolumeThreshold(
                    exchange.getIn().getHeader(HystrixConstants.CAMEL_HYSTRIX_CIRCUIT_BREAKER_REQUEST_VOLUME_THRESHOLD,
                            configuration.getCircuitBreakerRequestVolumeThreshold(), Integer.class));
        }

        if (exchange.getIn().getHeader(HystrixConstants.CAMEL_HYSTRIX_CIRCUIT_BREAKER_SLEEP_WINDOW_IN_MILLISECONDS,
                configuration.getCircuitBreakerSleepWindowInMilliseconds(), Integer.class) != null) {
            commandDefaults.withCircuitBreakerSleepWindowInMilliseconds(
                    exchange.getIn().getHeader(HystrixConstants.CAMEL_HYSTRIX_CIRCUIT_BREAKER_SLEEP_WINDOW_IN_MILLISECONDS,
                            configuration.getCircuitBreakerSleepWindowInMilliseconds(), Integer.class));
        }

        if (exchange.getIn().getHeader(HystrixConstants.CAMEL_HYSTRIX_EXECUTION_ISOLATION_SEMAPHORE_MAX_CONCURRENT_REQUESTS,
                configuration.getExecutionIsolationSemaphoreMaxConcurrentRequests(), Integer.class) != null) {
            commandDefaults.withExecutionIsolationSemaphoreMaxConcurrentRequests(
                    exchange.getIn().getHeader(HystrixConstants.CAMEL_HYSTRIX_EXECUTION_ISOLATION_SEMAPHORE_MAX_CONCURRENT_REQUESTS,
                            configuration.getExecutionIsolationSemaphoreMaxConcurrentRequests(), Integer.class));
        }

        if (exchange.getIn().getHeader(HystrixConstants.CAMEL_HYSTRIX_EXECUTION_ISOLATION_STRATEGY,
                configuration.getExecutionIsolationStrategy(), String.class) != null) {
            commandDefaults.withExecutionIsolationStrategy(HystrixCommandProperties.ExecutionIsolationStrategy.valueOf(
                    exchange.getIn().getHeader(HystrixConstants.CAMEL_HYSTRIX_EXECUTION_ISOLATION_STRATEGY,
                    configuration.getExecutionIsolationStrategy(), String.class)));
        }

        if (exchange.getIn().getHeader(HystrixConstants.CAMEL_HYSTRIX_EXECUTION_ISOLATION_THREAD_INTERRUPTION_ON_TIMEOUT,
                configuration.getExecutionIsolationThreadInterruptOnTimeout(), Boolean.class) != null) {
            commandDefaults.withExecutionIsolationThreadInterruptOnTimeout(
                    exchange.getIn().getHeader(HystrixConstants.CAMEL_HYSTRIX_EXECUTION_ISOLATION_THREAD_INTERRUPTION_ON_TIMEOUT,
                            configuration.getExecutionIsolationThreadInterruptOnTimeout(), Boolean.class));
        }

        if (exchange.getIn().getHeader(HystrixConstants.CAMEL_HYSTRIX_EXECUTION_TIMEOUT_IN_MILLISECONDS,
                configuration.getExecutionTimeoutInMilliseconds(), Integer.class) != null) {
            commandDefaults.withExecutionTimeoutInMilliseconds(
                    exchange.getIn().getHeader(HystrixConstants.CAMEL_HYSTRIX_EXECUTION_TIMEOUT_IN_MILLISECONDS,
                            configuration.getExecutionTimeoutInMilliseconds(), Integer.class));
        }

        if (exchange.getIn().getHeader(HystrixConstants.CAMEL_HYSTRIX_EXECUTION_TIMEOUT_ENABLED,
                configuration.getExecutionTimeoutEnabled(), Boolean.class) != null) {
            commandDefaults.withExecutionTimeoutEnabled(
                    exchange.getIn().getHeader(HystrixConstants.CAMEL_HYSTRIX_EXECUTION_TIMEOUT_ENABLED,
                            configuration.getExecutionTimeoutEnabled(), Boolean.class));
        }

        if (exchange.getIn().getHeader(HystrixConstants.CAMEL_HYSTRIX_FALLBACK_ISOLATION_SEMAPHORE_MAX_CONCURRENT_REQUESTS,
                configuration.getFallbackIsolationSemaphoreMaxConcurrentRequests(), Integer.class) != null) {
            commandDefaults.withFallbackIsolationSemaphoreMaxConcurrentRequests(
                    exchange.getIn().getHeader(HystrixConstants.CAMEL_HYSTRIX_FALLBACK_ISOLATION_SEMAPHORE_MAX_CONCURRENT_REQUESTS,
                            configuration.getFallbackIsolationSemaphoreMaxConcurrentRequests(), Integer.class));
        }

        if (exchange.getIn().getHeader(HystrixConstants.CAMEL_HYSTRIX_FALLBACK_ENABLED,
                configuration.getFallbackEnabled(), Boolean.class) != null) {
            commandDefaults.withFallbackEnabled(
                    exchange.getIn().getHeader(HystrixConstants.CAMEL_HYSTRIX_FALLBACK_ENABLED,
                            configuration.getFallbackEnabled(), Boolean.class));
        }

        if (exchange.getIn().getHeader(HystrixConstants.CAMEL_HYSTRIX_METRICS_HEALTH_SNAPSHOT_INTERVAL_IN_MILLISECONDS,
                configuration.getMetricsHealthSnapshotIntervalInMilliseconds(), Integer.class) != null) {
            commandDefaults.withMetricsHealthSnapshotIntervalInMilliseconds(
                    exchange.getIn().getHeader(HystrixConstants.CAMEL_HYSTRIX_METRICS_HEALTH_SNAPSHOT_INTERVAL_IN_MILLISECONDS,
                            configuration.getMetricsHealthSnapshotIntervalInMilliseconds(), Integer.class));
        }

        if (exchange.getIn().getHeader(HystrixConstants.CAMEL_HYSTRIX_METRICS_ROLLING_PERCENTILE_BUCKET_SIZE,
                configuration.getMetricsRollingPercentileBucketSize(), Integer.class) != null) {
            commandDefaults.withMetricsRollingPercentileBucketSize(
                    exchange.getIn().getHeader(HystrixConstants.CAMEL_HYSTRIX_METRICS_ROLLING_PERCENTILE_BUCKET_SIZE,
                            configuration.getMetricsRollingPercentileBucketSize(), Integer.class));
        }

        if (exchange.getIn().getHeader(HystrixConstants.CAMEL_HYSTRIX_METRICS_ROLLING_PERCENTILE_ENABLED,
                configuration.getMetricsRollingPercentileEnabled(), Boolean.class) != null) {
            commandDefaults.withMetricsRollingPercentileEnabled(
                    exchange.getIn().getHeader(HystrixConstants.CAMEL_HYSTRIX_METRICS_ROLLING_PERCENTILE_ENABLED,
                            configuration.getMetricsRollingPercentileEnabled(), Boolean.class));
        }

        if (exchange.getIn().getHeader(HystrixConstants.CAMEL_HYSTRIX_METRICS_ROLLING_PERCENTILE_WINDOW_IN_MILLISECONDS,
                configuration.getMetricsRollingPercentileWindowInMilliseconds(), Integer.class) != null) {
            commandDefaults.withMetricsRollingPercentileWindowInMilliseconds(
                    exchange.getIn().getHeader(HystrixConstants.CAMEL_HYSTRIX_METRICS_ROLLING_PERCENTILE_WINDOW_IN_MILLISECONDS,
                            configuration.getMetricsRollingPercentileWindowInMilliseconds(), Integer.class));
        }

        if (exchange.getIn().getHeader(HystrixConstants.CAMEL_HYSTRIX_METRICS_ROLLING_PERCENTILE_WINDOW_BUCKETS,
                configuration.getMetricsRollingPercentileWindowBuckets(), Integer.class) != null) {
            commandDefaults.withMetricsRollingPercentileWindowBuckets(
                    exchange.getIn().getHeader(HystrixConstants.CAMEL_HYSTRIX_METRICS_ROLLING_PERCENTILE_WINDOW_BUCKETS,
                            configuration.getMetricsRollingPercentileWindowBuckets(), Integer.class));
        }

        if (exchange.getIn().getHeader(HystrixConstants.CAMEL_HYSTRIX_METRICS_ROLLING_STATISTICAL_WINDOW_IN_MILLISECONDS,
                configuration.getMetricsRollingStatisticalWindowInMilliseconds(), Integer.class) != null) {
            commandDefaults.withMetricsRollingStatisticalWindowInMilliseconds(
                    exchange.getIn().getHeader(HystrixConstants.CAMEL_HYSTRIX_METRICS_ROLLING_STATISTICAL_WINDOW_IN_MILLISECONDS,
                            configuration.getMetricsRollingStatisticalWindowInMilliseconds(), Integer.class));
        }

        if (exchange.getIn().getHeader(HystrixConstants.CAMEL_HYSTRIX_METRICS_ROLLING_STATISTICAL_WINDOW_BUCKETS,
                configuration.getMetricsRollingStatisticalWindowBuckets(), Integer.class) != null) {
            commandDefaults.withMetricsRollingStatisticalWindowBuckets(
                    exchange.getIn().getHeader(HystrixConstants.CAMEL_HYSTRIX_METRICS_ROLLING_STATISTICAL_WINDOW_BUCKETS,
                            configuration.getMetricsRollingStatisticalWindowBuckets(), Integer.class));
        }

        if (exchange.getIn().getHeader(HystrixConstants.CAMEL_HYSTRIX_REQUEST_CACHE_ENABLED, configuration.getRequestCacheEnabled(), Boolean.class) != null) {
            commandDefaults.withRequestCacheEnabled(
                    exchange.getIn().getHeader(HystrixConstants.CAMEL_HYSTRIX_REQUEST_CACHE_ENABLED, configuration.getRequestCacheEnabled(), Boolean.class));
        }

        if (exchange.getIn().getHeader(HystrixConstants.CAMEL_HYSTRIX_REQUEST_LOG_ENABLED, configuration.getRequestLogEnabled(), Boolean.class) != null) {
            commandDefaults.withRequestLogEnabled(
                    exchange.getIn().getHeader(HystrixConstants.CAMEL_HYSTRIX_REQUEST_LOG_ENABLED, configuration.getRequestLogEnabled(), Boolean.class));
        }
    }

    private void setThreadPoolPropertiesDefaults(HystrixCommand.Setter setter, Exchange exchange) {
        HystrixThreadPoolProperties.Setter threadPoolProperties = HystrixThreadPoolProperties.Setter();
        setter.andThreadPoolPropertiesDefaults(threadPoolProperties);

        if (exchange.getIn().getHeader(HystrixConstants.CAMEL_HYSTRIX_THREAD_POOL_KEY, configuration.getThreadPoolKey(), String.class) != null) {
            setter.andThreadPoolKey(HystrixThreadPoolKey.Factory.asKey(
                    exchange.getIn().getHeader(HystrixConstants.CAMEL_HYSTRIX_THREAD_POOL_KEY, configuration.getThreadPoolKey(), String.class)));
        }

        if (exchange.getIn().getHeader(HystrixConstants.CAMEL_HYSTRIX_CORE_SIZE, configuration.getCoreSize(), Integer.class) != null) {
            threadPoolProperties.withCoreSize(
                    exchange.getIn().getHeader(HystrixConstants.CAMEL_HYSTRIX_CORE_SIZE, configuration.getCoreSize(), Integer.class));
        }

        if (exchange.getIn().getHeader(HystrixConstants.CAMEL_HYSTRIX_KEEP_ALIVE_TIME, configuration.getKeepAliveTime(), Integer.class) != null) {
            threadPoolProperties.withKeepAliveTimeMinutes(
                    exchange.getIn().getHeader(HystrixConstants.CAMEL_HYSTRIX_KEEP_ALIVE_TIME, configuration.getKeepAliveTime(), Integer.class));
        }

        if (exchange.getIn().getHeader(HystrixConstants.CAMEL_HYSTRIX_MAX_QUEUE_SIZE, configuration.getMaxQueueSize(), Integer.class) != null) {
            threadPoolProperties.withMaxQueueSize(
                    exchange.getIn().getHeader(HystrixConstants.CAMEL_HYSTRIX_MAX_QUEUE_SIZE, configuration.getMaxQueueSize(), Integer.class));
        }

        if (exchange.getIn().getHeader(HystrixConstants.CAMEL_HYSTRIX_QUEUE_SIZE_REJECTION_THRESHOLD,
                configuration.getQueueSizeRejectionThreshold(), Integer.class) != null) {
            threadPoolProperties.withQueueSizeRejectionThreshold(
                    exchange.getIn().getHeader(HystrixConstants.CAMEL_HYSTRIX_QUEUE_SIZE_REJECTION_THRESHOLD,
                            configuration.getQueueSizeRejectionThreshold(), Integer.class));
        }

        if (exchange.getIn().getHeader(HystrixConstants.CAMEL_HYSTRIX_THREAD_POOL_METRICS_ROLLING_STATISTICAL_WINDOW_IN_MILLISECONDS,
                configuration.getThreadPoolMetricsRollingStatisticalWindowInMilliseconds(), Integer.class) != null) {
            threadPoolProperties.withMetricsRollingStatisticalWindowInMilliseconds(
                    exchange.getIn().getHeader(HystrixConstants.CAMEL_HYSTRIX_THREAD_POOL_METRICS_ROLLING_STATISTICAL_WINDOW_IN_MILLISECONDS,
                            configuration.getThreadPoolMetricsRollingStatisticalWindowInMilliseconds(), Integer.class));
        }

        if (exchange.getIn().getHeader(HystrixConstants.CAMEL_HYSTRIX_THREAD_POOL_ROLLING_NUMBER_STATISTICAL_WINDOW_BUCKETS,
                configuration.getThreadPoolMetricsRollingStatisticalWindowBuckets(), Integer.class) != null) {
            threadPoolProperties.withMetricsRollingStatisticalWindowBuckets(
                    exchange.getIn().getHeader(HystrixConstants.CAMEL_HYSTRIX_THREAD_POOL_ROLLING_NUMBER_STATISTICAL_WINDOW_BUCKETS,
                            configuration.getThreadPoolMetricsRollingStatisticalWindowBuckets(), Integer.class));
        }
    }

    private String getCacheKey(Exchange exchange) {
        Object cacheKey = exchange.getIn().getHeader(HystrixConstants.CAMEL_HYSTRIX_CACHE_KEY, configuration.getCacheKey(), Object.class);
        if (cacheKey == null) {
            return null;
        }

        String answer;
        Expression expression;
        if (cacheKey instanceof Expression) {
            // it may be an expression already
            expression = (Expression) cacheKey;
        } else {
            // otherwise its a string that either refer to an expression to lookup or use the simple languagae
            String key = cacheKey.toString();
            if (EndpointHelper.isReferenceParameter(key)) {
                expression = CamelContextHelper.mandatoryLookup(exchange.getContext(), key.substring(1), Expression.class);
            } else {
                // use simple language as default for the expression
                expression = exchange.getContext().resolveLanguage("simple").createExpression(key);
            }
        }

        answer = expression.evaluate(exchange, String.class);
        return answer;
    }

    private synchronized void checkRequestContextPresent(Exchange exchange) {
        if (!HystrixRequestContext.isCurrentThreadInitialized()) {
            HystrixRequestContext customRequestContext = exchange.getIn()
                    .getHeader(HystrixConstants.CAMEL_HYSTRIX_REQUEST_CONTEXT, HystrixRequestContext.class);

            if (customRequestContext != null) {
                HystrixRequestContext.setContextOnCurrentThread(customRequestContext);
            } else if (requestContext != null) {
                HystrixRequestContext.setContextOnCurrentThread(requestContext);
                exchange.getIn().setHeader(HystrixConstants.CAMEL_HYSTRIX_REQUEST_CONTEXT, requestContext);
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

    private static void populateWithMetrics(Exchange exchange, CamelHystrixCommand camelHystrixCommand) {
        HystrixCommandMetrics commandMetrics = HystrixCommandMetrics.getInstance(camelHystrixCommand.getCommandKey());
        HystrixThreadPoolMetrics threadPoolMetrics = HystrixThreadPoolMetrics.getInstance(camelHystrixCommand.getThreadPoolKey());

        setHeader(exchange, HystrixConstants.CAMEL_HYSTRIX_COMMAND_METRICS_TOTAL_REQUESTS, commandMetrics.getHealthCounts().getTotalRequests());
        setHeader(exchange, HystrixConstants.CAMEL_HYSTRIX_COMMAND_METRICS_ERROR_COUNT, commandMetrics.getHealthCounts().getErrorCount());
        setHeader(exchange, HystrixConstants.CAMEL_HYSTRIX_COMMAND_METRICS_ERROR_PERCENTAGE, commandMetrics.getHealthCounts().getErrorPercentage());
        setHeader(exchange, HystrixConstants.CAMEL_HYSTRIX_COMMAND_METRICS_CURRENT_CONCURRENT_EXECUTION_COUNT, commandMetrics.getCurrentConcurrentExecutionCount());
        setHeader(exchange, HystrixConstants.CAMEL_HYSTRIX_COMMAND_METRICS_EXECUTION_TIME_MEAN, commandMetrics.getExecutionTimeMean());
        setHeader(exchange, HystrixConstants.CAMEL_HYSTRIX_COMMAND_METRICS_ROLLING_MAX_CONCURRENT_EXECUTIONS, commandMetrics.getRollingMaxConcurrentExecutions());
        setHeader(exchange, HystrixConstants.CAMEL_HYSTRIX_COMMAND_METRICS_TOTAL_TIME_MEAN, commandMetrics.getTotalTimeMean());

        setHeader(exchange, HystrixConstants.CAMEL_HYSTRIX_THREAD_POOL_METRICS_CURRENT_ACTIVE_COUNT, threadPoolMetrics.getCurrentActiveCount());
        setHeader(exchange, HystrixConstants.CAMEL_HYSTRIX_THREAD_POOL_METRICS_CUMULATIVE_COUNT_THREADS_EXECUTED, threadPoolMetrics.getCumulativeCountThreadsExecuted());
        setHeader(exchange, HystrixConstants.CAMEL_HYSTRIX_THREAD_POOL_METRICS_CURRENT_COMPLETED_TASK_COUNT, threadPoolMetrics.getCurrentCompletedTaskCount());
        setHeader(exchange, HystrixConstants.CAMEL_HYSTRIX_THREAD_POOL_METRICS_CURRENT_CORE_POOL_SIZE, threadPoolMetrics.getCurrentCorePoolSize());
        setHeader(exchange, HystrixConstants.CAMEL_HYSTRIX_THREAD_POOL_METRICS_CURRENT_LARGEST_POOL_SIZE, threadPoolMetrics.getCurrentLargestPoolSize());
        setHeader(exchange, HystrixConstants.CAMEL_HYSTRIX_THREAD_POOL_METRICS_CURRENT_MAXIMUM_POOL_SIZE, threadPoolMetrics.getCurrentMaximumPoolSize());
        setHeader(exchange, HystrixConstants.CAMEL_HYSTRIX_THREAD_POOL_METRICS_CURRENT_POOL_SIZE, threadPoolMetrics.getCurrentPoolSize());
        setHeader(exchange, HystrixConstants.CAMEL_HYSTRIX_THREAD_POOL_METRICS_CURRENT_QUEUE_SIZE, threadPoolMetrics.getCurrentQueueSize());
        setHeader(exchange, HystrixConstants.CAMEL_HYSTRIX_THREAD_POOL_METRICS_CURRENT_TASK_COUNT, threadPoolMetrics.getCurrentTaskCount());
        setHeader(exchange, HystrixConstants.CAMEL_HYSTRIX_THREAD_POOL_METRICS_ROLLING_COUNT_THREADS_EXECUTED, threadPoolMetrics.getRollingCountThreadsExecuted());
        setHeader(exchange, HystrixConstants.CAMEL_HYSTRIX_THREAD_POOL_METRICS_ROLLING_MAX_ACTIVE_THREADS, threadPoolMetrics.getRollingMaxActiveThreads());
    }

    private static void setHeader(Exchange exchange, String key, Object value) {
        if (exchange.hasOut()) {
            exchange.getOut().setHeader(key, value);
        } else {
            exchange.getIn().setHeader(key, value);
        }
    }

    @Override
    protected void doStart() throws Exception {
        // setup the producer cache
        producerCache = new ProducerCache(this, getEndpoint().getCamelContext());
        ServiceHelper.startService(producerCache);

        if (configuration.getInitializeRequestContext() != null && configuration.getInitializeRequestContext()) {
            requestContext = HystrixRequestContext.initializeContext();
        }
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        if (requestContext != null) {
            requestContext.shutdown();
        }
        ServiceHelper.stopService(producerCache);
        super.doStop();
    }

}
