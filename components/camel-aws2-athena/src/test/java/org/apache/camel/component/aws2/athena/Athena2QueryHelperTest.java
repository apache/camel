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
package org.apache.camel.component.aws2.athena;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.athena.model.GetQueryExecutionResponse;
import software.amazon.awssdk.services.athena.model.QueryExecution;
import software.amazon.awssdk.services.athena.model.QueryExecutionState;
import software.amazon.awssdk.services.athena.model.QueryExecutionStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Athena2QueryHelperTest {

    @Test
    public void parameterDefaults() {
        Athena2QueryHelper helper = defaultAthena2QueryHelper();

        // configuration ======================
        assertEquals(0, helper.getWaitTimeout());
        assertEquals(2_000, helper.getDelay());
        assertEquals(1, helper.getMaxAttempts());
        assertEquals(new HashSet<>(Collections.singletonList("never")), helper.getRetry());
        assertTrue(helper.isResetWaitTimeoutOnAttempt());
        assertTrue(helper.getAbsoluteStartMs() <= System.currentTimeMillis());

        // state ==============================
        assertEquals(1_000, helper.getCurrentDelay());
        assertEquals(0, helper.getAttempts());
        assertFalse(helper.isFailure());
        assertFalse(helper.isSuccess());
        assertFalse(helper.isRetry());
        assertEquals(0, helper.getStartMs());
        assertFalse(helper.isInterrupted());
    }

    @Test
    public void determineRetryWhenNotSet() {
        Athena2QueryHelper helper = athena2QueryHelperWithRetry(null);
        assertEquals(new HashSet<>(Collections.singletonList("never")), helper.getRetry());
    }

    @Test
    public void determineRetryWhenSetToNever() {
        Athena2QueryHelper helper = athena2QueryHelperWithRetry("never");
        assertEquals(new HashSet<>(Collections.singletonList("never")), helper.getRetry());
    }

    @Test
    public void determineRetryWhenSetToAlways() {
        Athena2QueryHelper helper = athena2QueryHelperWithRetry("always");
        assertEquals(new HashSet<>(Collections.singletonList("always")), helper.getRetry());
    }

    @Test
    public void determineRetryWhenSetToRetryable() {
        Athena2QueryHelper helper = athena2QueryHelperWithRetry("retryable");
        assertEquals(new HashSet<>(Collections.singletonList("retryable")), helper.getRetry());
    }

    @Test
    public void determineRetryWhenSetToGeneric() {
        Athena2QueryHelper helper = athena2QueryHelperWithRetry("generic");
        assertEquals(new HashSet<>(Collections.singletonList("generic")), helper.getRetry());
    }

    @Test
    public void determineRetryWhenSetToExhausted() {
        Athena2QueryHelper helper = athena2QueryHelperWithRetry("exhausted");
        assertEquals(new HashSet<>(Collections.singletonList("exhausted")), helper.getRetry());
    }

    @Test
    public void determineRetryWhenSetToMultiple() {
        Athena2QueryHelper helper = athena2QueryHelperWithRetry("exhausted,generic");
        assertEquals(new HashSet<>(Arrays.asList("exhausted", "generic")), helper.getRetry());
    }

    @Test
    public void determineRetryDoesNotAllowMutuallyExclusiveValues() {
        assertThrows(IllegalArgumentException.class, () -> athena2QueryHelperWithRetry("always,never"));
    }

    @Test
    public void determineRetryDoesNotAllowUnknownValues() {
        assertThrows(IllegalArgumentException.class, () -> athena2QueryHelperWithRetry("foo"));
    }

    @Test
    public void markAttemptIncrementsAttemptsAndResetsStatuses() {
        Athena2QueryHelper helper = defaultAthena2QueryHelper();

        assertEquals(0, helper.getAttempts());

        assertFalse(helper.isSuccess());
        helper.setStatusFrom(newGetQueryExecutionResponse(QueryExecutionState.SUCCEEDED));
        assertTrue(helper.isSuccess());

        helper.markAttempt();

        assertFalse(helper.isSuccess());
        assertEquals(1, helper.getAttempts());
    }

    @Test
    public void testStartQueryExecutionHappyPath() {
        Athena2Configuration configuration = new Athena2Configuration();
        configuration.setMaxAttempts(3);
        configuration.setWaitTimeout(2_000);
        configuration.setInitialDelay(1);
        configuration.setDelay(1);

        Athena2QueryHelper helper = new Athena2QueryHelper(
                new DefaultExchange(new DefaultCamelContext()),
                configuration);

        assertTrue(helper.shouldAttempt());

        helper.markAttempt();

        assertTrue(helper.shouldWait());
        helper.doWait();

        helper.setStatusFrom(newGetQueryExecutionResponse(QueryExecutionState.SUCCEEDED));

        assertFalse(helper.shouldWait());
        assertTrue(helper.isSuccess());
        assertFalse(helper.isFailure());
        assertFalse(helper.isRetry());
        assertFalse(helper.isInterrupted());

        assertFalse(helper.shouldAttempt());
    }

    @Test
    public void isComplete() {
        Athena2QueryHelper helper = defaultAthena2QueryHelper();

        assertTrue(helper.isComplete(newGetQueryExecutionResponse(QueryExecutionState.SUCCEEDED)));
        assertTrue(helper.isComplete(newGetQueryExecutionResponse(QueryExecutionState.FAILED)));
        assertTrue(helper.isComplete(newGetQueryExecutionResponse(QueryExecutionState.CANCELLED)));
        assertTrue(helper.isComplete(newGetQueryExecutionResponse(QueryExecutionState.UNKNOWN_TO_SDK_VERSION)));

        assertFalse(helper.isComplete(newGetQueryExecutionResponse(QueryExecutionState.QUEUED)));
        assertFalse(helper.isComplete(newGetQueryExecutionResponse(QueryExecutionState.RUNNING)));
    }

    @Test
    public void wasSuccessful() {
        Athena2QueryHelper helper = defaultAthena2QueryHelper();

        assertTrue(helper.wasSuccessful(newGetQueryExecutionResponse(QueryExecutionState.SUCCEEDED)));

        assertFalse(helper.wasSuccessful(newGetQueryExecutionResponse(QueryExecutionState.FAILED)));
        assertFalse(helper.wasSuccessful(newGetQueryExecutionResponse(QueryExecutionState.CANCELLED)));
        assertFalse(helper.wasSuccessful(newGetQueryExecutionResponse(QueryExecutionState.UNKNOWN_TO_SDK_VERSION)));
        assertFalse(helper.wasSuccessful(newGetQueryExecutionResponse(QueryExecutionState.QUEUED)));
        assertFalse(helper.wasSuccessful(newGetQueryExecutionResponse(QueryExecutionState.RUNNING)));
    }

    @Test
    public void setStatusFromWhenQuerySucceeded() {
        Athena2QueryHelper helper = defaultAthena2QueryHelper();

        assertFalse(helper.isSuccess());
        assertFalse(helper.isRetry());
        assertFalse(helper.isFailure());

        helper.setStatusFrom(newGetQueryExecutionResponse(QueryExecutionState.SUCCEEDED));

        assertTrue(helper.isSuccess());
        assertFalse(helper.isRetry());
        assertFalse(helper.isFailure());
    }

    @Test
    public void setStatusFromWhenQueryShouldRetry() {
        Athena2QueryHelper helper = athena2QueryHelperWithRetry("always");

        assertFalse(helper.isRetry());
        assertFalse(helper.isRetry());
        assertFalse(helper.isFailure());

        helper.setStatusFrom(newGetQueryExecutionResponse(QueryExecutionState.CANCELLED));

        assertFalse(helper.isSuccess());
        assertTrue(helper.isRetry());
        assertFalse(helper.isFailure());
    }

    @Test
    public void setStatusFromWhenRetryIsNever() {
        Athena2QueryHelper helper = athena2QueryHelperWithRetry("never");

        assertFalse(helper.isRetry());
        assertFalse(helper.isRetry());
        assertFalse(helper.isFailure());

        helper.setStatusFrom(newGetQueryExecutionResponse(QueryExecutionState.CANCELLED));

        assertFalse(helper.isSuccess());
        assertFalse(helper.isRetry());
        assertTrue(helper.isFailure());
    }

    @Test
    public void shouldRetryReturnsFalseWhenRetryIsNever() {
        Athena2QueryHelper helper = athena2QueryHelperWithRetry("never");
        assertFalse(helper.shouldRetry(newGetQueryExecutionResponse(QueryExecutionState.FAILED, "GENERIC_INTERNAL_ERROR")));
    }

    @Test
    void shouldRetryReturnsTrueWhenRetryIsAlways() {
        Athena2QueryHelper helper = athena2QueryHelperWithRetry("always");
        assertTrue(helper.shouldRetry(newGetQueryExecutionResponse(QueryExecutionState.FAILED, null)));
    }

    @Test
    public void shouldRetryReturnsTrueForGenericInternalError() {
        Athena2QueryHelper helper = athena2QueryHelperWithRetry("retryable");
        assertTrue(helper.shouldRetry(newGetQueryExecutionResponse(QueryExecutionState.FAILED, "GENERIC_INTERNAL_ERROR")));
    }

    @Test
    public void shouldRetryReturnsTrueForExhaustedResourcedError() {
        Athena2QueryHelper helper = athena2QueryHelperWithRetry("retryable");
        assertTrue(helper.shouldRetry(
                newGetQueryExecutionResponse(QueryExecutionState.FAILED, "exhausted resources at this scale factor")));
    }

    @Test
    public void shouldRetryReturnsFalseForUnexpectedError() {
        Athena2QueryHelper helper = athena2QueryHelperWithRetry("retryable");
        assertFalse(helper.shouldRetry(newGetQueryExecutionResponse(QueryExecutionState.FAILED, "unexpected")));
    }

    private Athena2QueryHelper defaultAthena2QueryHelper() {
        return new Athena2QueryHelper(
                new DefaultExchange(new DefaultCamelContext()),
                new Athena2Configuration());
    }

    private Athena2QueryHelper athena2QueryHelperWithRetry(String retry) {
        Athena2Configuration configuration = new Athena2Configuration();
        configuration.setRetry(retry);

        return new Athena2QueryHelper(
                new DefaultExchange(new DefaultCamelContext()),
                configuration);
    }

    private GetQueryExecutionResponse newGetQueryExecutionResponse(QueryExecutionState queryExecutionState) {
        return newGetQueryExecutionResponse(queryExecutionState, null);
    }

    private GetQueryExecutionResponse newGetQueryExecutionResponse(
            QueryExecutionState queryExecutionState,
            String stateChangeReason) {
        return GetQueryExecutionResponse.builder()
                .queryExecution(QueryExecution.builder()
                        .status(QueryExecutionStatus.builder()
                                .state(queryExecutionState)
                                .stateChangeReason(stateChangeReason)
                                .build())
                        .build())
                .build();
    }
}
