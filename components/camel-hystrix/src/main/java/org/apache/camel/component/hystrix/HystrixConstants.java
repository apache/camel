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

public interface HystrixConstants {

    // Hystrix EIP response properties
    String HYSTRIX_RESPONSE_SUCCESSFUL_EXECUTION = "CamelHystrixSuccessfulExecution";
    String HYSTRIX_RESPONSE_FROM_FALLBACK = "CamelHystrixResponseFromFallback";
    String HYSTRIX_RESPONSE_SHORT_CIRCUITED = "CamelHystrixResponseShortCircuited";
    String HYSTRIX_RESPONSE_TIMED_OUT = "CamelHystrixResponseTimedOut";
    String HYSTRIX_RESPONSE_REJECTED = "CamelHystrixResponseRejected";

    // in message header
    String CAMEL_HYSTRIX_RUN_ENDPOINT = "CamelHystrixRunEndpoint";
    String CAMEL_HYSTRIX_FALLBACK_ENDPOINT = "CamelHystrixFallbackEndpoint";
    String CAMEL_HYSTRIX_CACHE_KEY = "CamelHystrixCacheKey";
    String CAMEL_HYSTRIX_CLEAR_CACHE_FIRST = "CamelHystrixClearCacheFirst";
    String CAMEL_HYSTRIX_REQUEST_CONTEXT = "CamelHystrixRequestContex";
    String CAMEL_HYSTRIX_GROUP_KEY = "CamelHystrixGroupKey";
    String CAMEL_HYSTRIX_COMMAND_KEY = "CamelHystrixCommandKey";
    String CAMEL_HYSTRIX_THREAD_POOL_KEY = "CamelHystrixThreadPoolKey";
    String CAMEL_HYSTRIX_CORE_SIZE = "CamelHystrixCoreSize";
    String CAMEL_HYSTRIX_KEEP_ALIVE_TIME = "CamelHystrixKeepAliveTime";
    String CAMEL_HYSTRIX_MAX_QUEUE_SIZE = "CamelHystrixMaxQueueSize";
    String CAMEL_HYSTRIX_QUEUE_SIZE_REJECTION_THRESHOLD = "CamelHystrixQueueSizeRejectionThreshold";

    String CAMEL_HYSTRIX_THREAD_POOL_METRICS_ROLLING_STATISTICAL_WINDOW_IN_MILLISECONDS = "CamelHystrixThreadPoolMetricsRollingStatisticalWindowInMilliseconds";
    String CAMEL_HYSTRIX_THREAD_POOL_ROLLING_NUMBER_STATISTICAL_WINDOW_BUCKETS = "CamelHystrixThreadPoolRollingNumberStatisticalWindowBuckets";

    String CAMEL_HYSTRIX_CIRCUIT_BREAKER_ENABLED = "CamelHystrixCircuitBreakerEnabled";
    String CAMEL_HYSTRIX_CIRCUIT_BREAKER_ERROR_THRESHOLD_PERCENTAGE = "CamelHystrixCircuitBreakerErrorThresholdPercentage";
    String CAMEL_HYSTRIX_CIRCUIT_BREAKER_FORCE_CLOSED = "CamelHystrixCircuitBreakerForceClosed";
    String CAMEL_HYSTRIX_CIRCUIT_BREAKER_FORCE_OPEN = "CamelHystrixCircuitBreakerForceOpen";
    String CAMEL_HYSTRIX_CIRCUIT_BREAKER_REQUEST_VOLUME_THRESHOLD = "CamelHystrixCircuitBreakerRequestVolumeThreshold";
    String CAMEL_HYSTRIX_CIRCUIT_BREAKER_SLEEP_WINDOW_IN_MILLISECONDS = "CamelHystrixCircuitBreakerSleepWindowInMilliseconds";
    String CAMEL_HYSTRIX_EXECUTION_ISOLATION_SEMAPHORE_MAX_CONCURRENT_REQUESTS = "CamelHystrixExecutionIsolationSemaphoreMaxConcurrentRequests";
    String CAMEL_HYSTRIX_EXECUTION_ISOLATION_STRATEGY = "CamelHystrixExecutionIsolationStrategy";
    String CAMEL_HYSTRIX_EXECUTION_ISOLATION_THREAD_INTERRUPTION_ON_TIMEOUT = "CamelHystrixExecutionIsolationThreadInterruptOnTimeout";
    String CAMEL_HYSTRIX_EXECUTION_TIMEOUT_IN_MILLISECONDS = "CamelHystrixExecutionTimeoutInMilliseconds";
    String CAMEL_HYSTRIX_EXECUTION_TIMEOUT_ENABLED = "CamelHystrixExecutionTimeoutEnabled";
    String CAMEL_HYSTRIX_FALLBACK_ISOLATION_SEMAPHORE_MAX_CONCURRENT_REQUESTS = "CamelHystrixFallbackIsolationSemaphoreMaxConcurrentRequests";
    String CAMEL_HYSTRIX_FALLBACK_ENABLED = "CamelHystrixFallbackEnabled";
    String CAMEL_HYSTRIX_METRICS_HEALTH_SNAPSHOT_INTERVAL_IN_MILLISECONDS = "CamelHystrixMetricsHealthSnapshotIntervalInMilliseconds";
    String CAMEL_HYSTRIX_METRICS_ROLLING_PERCENTILE_BUCKET_SIZE = "CamelHystrixMetricsRollingPercentileBucketSize";
    String CAMEL_HYSTRIX_METRICS_ROLLING_PERCENTILE_ENABLED = "CamelHystrixMetricsRollingPercentileEnabled";
    String CAMEL_HYSTRIX_METRICS_ROLLING_PERCENTILE_WINDOW_IN_MILLISECONDS = "CamelHystrixMetricsRollingPercentileWindowInMilliseconds";
    String CAMEL_HYSTRIX_METRICS_ROLLING_PERCENTILE_WINDOW_BUCKETS = "CamelHystrixMetricsRollingPercentileWindowBuckets";
    String CAMEL_HYSTRIX_METRICS_ROLLING_STATISTICAL_WINDOW_IN_MILLISECONDS = "CamelHystrixMetricsRollingStatisticalWindowInMilliseconds";
    String CAMEL_HYSTRIX_METRICS_ROLLING_STATISTICAL_WINDOW_BUCKETS = "CamelHystrixMetricsRollingStatisticalWindowBuckets";
    String CAMEL_HYSTRIX_REQUEST_CACHE_ENABLED = "CamelHystrixRequestCacheEnabled";
    String CAMEL_HYSTRIX_REQUEST_LOG_ENABLED = "CamelHystrixRequestLogEnabled";

    //out message headers
    String CAMEL_HYSTRIX_COMMAND_METRICS_TOTAL_REQUESTS = "CamelHystrixCommandMetricsTotalRequests";
    String CAMEL_HYSTRIX_COMMAND_METRICS_ERROR_COUNT = "CamelHystrixCommandMetricsErrorCount";
    String CAMEL_HYSTRIX_COMMAND_METRICS_ERROR_PERCENTAGE = "CamelHystrixCommandMetricsErrorPercentage";
    String CAMEL_HYSTRIX_COMMAND_METRICS_CURRENT_CONCURRENT_EXECUTION_COUNT = "CamelHystrixCommandMetricsCurrentConcurrentExecutionCount";
    String CAMEL_HYSTRIX_COMMAND_METRICS_EXECUTION_TIME_MEAN = "CamelHystrixCommandMetricsExecutionTimeMean";
    String CAMEL_HYSTRIX_COMMAND_METRICS_ROLLING_MAX_CONCURRENT_EXECUTIONS = "CamelHystrixCommandMetricsRollingMaxConcurrentExecutions";
    String CAMEL_HYSTRIX_COMMAND_METRICS_TOTAL_TIME_MEAN = "CamelHystrixCommandMetricsTotalTimeMean";

    String CAMEL_HYSTRIX_THREAD_POOL_METRICS_CURRENT_ACTIVE_COUNT = "CamelHystrixThreadPoolMetricsCurrentActiveCount";
    String CAMEL_HYSTRIX_THREAD_POOL_METRICS_CUMULATIVE_COUNT_THREADS_EXECUTED = "CamelHystrixThreadPoolMetricsCumulativeCountThreadsExecuted";
    String CAMEL_HYSTRIX_THREAD_POOL_METRICS_CURRENT_COMPLETED_TASK_COUNT = "CamelHystrixThreadPoolMetricsCurrentCompletedTaskCount";
    String CAMEL_HYSTRIX_THREAD_POOL_METRICS_CURRENT_CORE_POOL_SIZE = "CamelHystrixThreadPoolMetricsCurrentCorePoolSize";
    String CAMEL_HYSTRIX_THREAD_POOL_METRICS_CURRENT_LARGEST_POOL_SIZE = "CamelHystrixThreadPoolMetricsCurrentLargestPoolSize";
    String CAMEL_HYSTRIX_THREAD_POOL_METRICS_CURRENT_MAXIMUM_POOL_SIZE = "CamelHystrixThreadPoolMetricsCurrentMaximumPoolSize";
    String CAMEL_HYSTRIX_THREAD_POOL_METRICS_CURRENT_POOL_SIZE = "CamelHystrixThreadPoolMetricsCurrentPoolSize";
    String CAMEL_HYSTRIX_THREAD_POOL_METRICS_CURRENT_QUEUE_SIZE = "CamelHystrixThreadPoolMetricsCurrentQueueSize";
    String CAMEL_HYSTRIX_THREAD_POOL_METRICS_CURRENT_TASK_COUNT = "CamelHystrixThreadPoolMetricsCurrentTaskCount";
    String CAMEL_HYSTRIX_THREAD_POOL_METRICS_ROLLING_COUNT_THREADS_EXECUTED = "CamelHystrixThreadPoolMetricsRollingCountThreadsExecuted";
    String CAMEL_HYSTRIX_THREAD_POOL_METRICS_ROLLING_MAX_ACTIVE_THREADS = "CamelHystrixThreadPoolMetricsRollingMaxActiveThreads";
}
