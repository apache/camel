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
package org.apache.camel;

import org.apache.camel.spi.CircuitBreakerConstants;

/**
 * An enum of common and known keys for exchange properties used by camel-core.
 */
public enum ExchangePropertyKey {

    AGGREGATED_COMPLETED_BY(Exchange.AGGREGATED_COMPLETED_BY),
    AGGREGATED_CORRELATION_KEY(Exchange.AGGREGATED_CORRELATION_KEY),
    AGGREGATED_SIZE(Exchange.AGGREGATED_SIZE),
    AGGREGATED_TIMEOUT(Exchange.AGGREGATED_TIMEOUT),
    AGGREGATION_COMPLETE_ALL_GROUPS(Exchange.AGGREGATION_COMPLETE_ALL_GROUPS),
    AGGREGATION_COMPLETE_CURRENT_GROUP(Exchange.AGGREGATION_COMPLETE_CURRENT_GROUP),
    AGGREGATION_STRATEGY(Exchange.AGGREGATION_STRATEGY),
    BATCH_COMPLETE(Exchange.BATCH_COMPLETE),
    BATCH_INDEX(Exchange.BATCH_INDEX),
    BATCH_SIZE(Exchange.BATCH_SIZE),
    CHARSET_NAME(Exchange.CHARSET_NAME),
    CIRCUIT_BREAKER_RESPONSE_SUCCESSFUL_EXECUTION(CircuitBreakerConstants.RESPONSE_SUCCESSFUL_EXECUTION),
    CIRCUIT_BREAKER_RESPONSE_FROM_FALLBACK(CircuitBreakerConstants.RESPONSE_FROM_FALLBACK),
    CIRCUIT_BREAKER_RESPONSE_SHORT_CIRCUITED(CircuitBreakerConstants.RESPONSE_SHORT_CIRCUITED),
    CIRCUIT_BREAKER_RESPONSE_TIMED_OUT(CircuitBreakerConstants.RESPONSE_TIMED_OUT),
    CIRCUIT_BREAKER_RESPONSE_REJECTED(CircuitBreakerConstants.RESPONSE_REJECTED),
    CLAIM_CHECK_REPOSITORY(Exchange.CLAIM_CHECK_REPOSITORY),
    CORRELATION_ID(Exchange.CORRELATION_ID),
    DUPLICATE_MESSAGE(Exchange.DUPLICATE_MESSAGE),
    ERRORHANDLER_BRIDGE(Exchange.ERRORHANDLER_BRIDGE),
    ERRORHANDLER_CIRCUIT_DETECTED(Exchange.ERRORHANDLER_CIRCUIT_DETECTED),
    EVALUATE_EXPRESSION_RESULT(Exchange.EVALUATE_EXPRESSION_RESULT),
    EXCEPTION_CAUGHT(Exchange.EXCEPTION_CAUGHT),
    EXCEPTION_HANDLED(Exchange.EXCEPTION_HANDLED),
    FAILURE_ENDPOINT(Exchange.FAILURE_ENDPOINT),
    FAILURE_HANDLED(Exchange.FAILURE_HANDLED),
    FAILURE_ROUTE_ID(Exchange.FAILURE_ROUTE_ID),
    FATAL_FALLBACK_ERROR_HANDLER(Exchange.FATAL_FALLBACK_ERROR_HANDLER),
    GROUPED_EXCHANGE(Exchange.GROUPED_EXCHANGE),
    INTERCEPT_SEND_TO_ENDPOINT_WHEN_MATCHED(Exchange.INTERCEPT_SEND_TO_ENDPOINT_WHEN_MATCHED),
    LOOP_INDEX(Exchange.LOOP_INDEX),
    LOOP_SIZE(Exchange.LOOP_SIZE),
    MESSAGE_HISTORY(Exchange.MESSAGE_HISTORY),
    MULTICAST_COMPLETE(Exchange.MULTICAST_COMPLETE),
    MULTICAST_INDEX(Exchange.MULTICAST_INDEX),
    ON_COMPLETION(Exchange.ON_COMPLETION),
    ON_COMPLETION_ROUTE_IDS(Exchange.ON_COMPLETION_ROUTE_IDS),
    PARENT_UNIT_OF_WORK(Exchange.PARENT_UNIT_OF_WORK),
    RECEIVED_TIMESTAMP(Exchange.RECEIVED_TIMESTAMP),
    RECIPIENT_LIST_ENDPOINT(Exchange.RECIPIENT_LIST_ENDPOINT),
    SLIP_ENDPOINT(Exchange.SLIP_ENDPOINT),
    SLIP_PRODUCER(Exchange.SLIP_PRODUCER),
    SPLIT_COMPLETE(Exchange.SPLIT_COMPLETE),
    SPLIT_INDEX(Exchange.SPLIT_INDEX),
    SPLIT_SIZE(Exchange.SPLIT_SIZE),
    STEP_ID(Exchange.STEP_ID),
    STREAM_CACHE_UNIT_OF_WORK(Exchange.STREAM_CACHE_UNIT_OF_WORK),
    TO_ENDPOINT(Exchange.TO_ENDPOINT),
    TRY_ROUTE_BLOCK(Exchange.TRY_ROUTE_BLOCK),
    UNIT_OF_WORK_EXHAUSTED(Exchange.UNIT_OF_WORK_EXHAUSTED);

    private final String name;

    ExchangePropertyKey(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public static ExchangePropertyKey asExchangePropertyKey(String name) {
        switch (name) {
            case Exchange.AGGREGATED_COMPLETED_BY:
                return AGGREGATED_COMPLETED_BY;
            case Exchange.AGGREGATED_CORRELATION_KEY:
                return AGGREGATED_CORRELATION_KEY;
            case Exchange.AGGREGATED_SIZE:
                return AGGREGATED_SIZE;
            case Exchange.AGGREGATED_TIMEOUT:
                return AGGREGATED_TIMEOUT;
            case Exchange.AGGREGATION_COMPLETE_ALL_GROUPS:
                return AGGREGATION_COMPLETE_ALL_GROUPS;
            case Exchange.AGGREGATION_COMPLETE_CURRENT_GROUP:
                return AGGREGATION_COMPLETE_CURRENT_GROUP;
            case Exchange.AGGREGATION_STRATEGY:
                return AGGREGATION_STRATEGY;
            case Exchange.BATCH_COMPLETE:
                return BATCH_COMPLETE;
            case Exchange.BATCH_INDEX:
                return BATCH_INDEX;
            case Exchange.BATCH_SIZE:
                return BATCH_SIZE;
            case Exchange.CHARSET_NAME:
                return CHARSET_NAME;
            case CircuitBreakerConstants.RESPONSE_SUCCESSFUL_EXECUTION:
                return CIRCUIT_BREAKER_RESPONSE_SUCCESSFUL_EXECUTION;
            case CircuitBreakerConstants.RESPONSE_FROM_FALLBACK:
                return CIRCUIT_BREAKER_RESPONSE_FROM_FALLBACK;
            case CircuitBreakerConstants.RESPONSE_SHORT_CIRCUITED:
                return CIRCUIT_BREAKER_RESPONSE_SHORT_CIRCUITED;
            case CircuitBreakerConstants.RESPONSE_TIMED_OUT:
                return CIRCUIT_BREAKER_RESPONSE_TIMED_OUT;
            case CircuitBreakerConstants.RESPONSE_REJECTED:
                return CIRCUIT_BREAKER_RESPONSE_REJECTED;
            case Exchange.CLAIM_CHECK_REPOSITORY:
                return CLAIM_CHECK_REPOSITORY;
            case Exchange.CORRELATION_ID:
                return CORRELATION_ID;
            case Exchange.DUPLICATE_MESSAGE:
                return DUPLICATE_MESSAGE;
            case Exchange.ERRORHANDLER_BRIDGE:
                return ERRORHANDLER_BRIDGE;
            case Exchange.ERRORHANDLER_CIRCUIT_DETECTED:
                return ERRORHANDLER_CIRCUIT_DETECTED;
            case Exchange.EVALUATE_EXPRESSION_RESULT:
                return EVALUATE_EXPRESSION_RESULT;
            case Exchange.EXCEPTION_CAUGHT:
                return EXCEPTION_CAUGHT;
            case Exchange.EXCEPTION_HANDLED:
                return EXCEPTION_HANDLED;
            case Exchange.FAILURE_ENDPOINT:
                return FAILURE_ENDPOINT;
            case Exchange.FAILURE_ROUTE_ID:
                return FAILURE_ROUTE_ID;
            case Exchange.FATAL_FALLBACK_ERROR_HANDLER:
                return FATAL_FALLBACK_ERROR_HANDLER;
            case Exchange.GROUPED_EXCHANGE:
                return GROUPED_EXCHANGE;
            case Exchange.INTERCEPT_SEND_TO_ENDPOINT_WHEN_MATCHED:
                return INTERCEPT_SEND_TO_ENDPOINT_WHEN_MATCHED;
            case Exchange.LOOP_INDEX:
                return LOOP_INDEX;
            case Exchange.LOOP_SIZE:
                return LOOP_SIZE;
            case Exchange.MESSAGE_HISTORY:
                return MESSAGE_HISTORY;
            case Exchange.MULTICAST_COMPLETE:
                return MULTICAST_COMPLETE;
            case Exchange.MULTICAST_INDEX:
                return MULTICAST_INDEX;
            case Exchange.ON_COMPLETION:
                return ON_COMPLETION;
            case Exchange.ON_COMPLETION_ROUTE_IDS:
                return ON_COMPLETION_ROUTE_IDS;
            case Exchange.PARENT_UNIT_OF_WORK:
                return PARENT_UNIT_OF_WORK;
            case Exchange.RECEIVED_TIMESTAMP:
                return RECEIVED_TIMESTAMP;
            case Exchange.RECIPIENT_LIST_ENDPOINT:
                return RECIPIENT_LIST_ENDPOINT;
            case Exchange.SLIP_ENDPOINT:
                return SLIP_ENDPOINT;
            case Exchange.SLIP_PRODUCER:
                return SLIP_PRODUCER;
            case Exchange.SPLIT_COMPLETE:
                return SPLIT_COMPLETE;
            case Exchange.SPLIT_INDEX:
                return SPLIT_INDEX;
            case Exchange.SPLIT_SIZE:
                return SPLIT_SIZE;
            case Exchange.STEP_ID:
                return STEP_ID;
            case Exchange.STREAM_CACHE_UNIT_OF_WORK:
                return STREAM_CACHE_UNIT_OF_WORK;
            case Exchange.TO_ENDPOINT:
                return TO_ENDPOINT;
            case Exchange.TRY_ROUTE_BLOCK:
                return TRY_ROUTE_BLOCK;
            case Exchange.UNIT_OF_WORK_EXHAUSTED:
                return UNIT_OF_WORK_EXHAUSTED;
            default:
                return null;
        }
    }
}
