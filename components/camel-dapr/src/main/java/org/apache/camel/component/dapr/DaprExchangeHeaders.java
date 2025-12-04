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

package org.apache.camel.component.dapr;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import io.dapr.client.domain.HttpExtension;
import io.dapr.client.domain.State;
import io.dapr.client.domain.StateOptions.Concurrency;
import io.dapr.client.domain.StateOptions.Consistency;
import io.dapr.client.domain.TransactionalStateOperation;
import org.apache.camel.Exchange;
import org.apache.camel.util.ObjectHelper;

public class DaprExchangeHeaders {

    public static String getServiceToInvokeFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DaprConstants.SERVICE_TO_INVOKE, String.class);
    }

    public static String getMethodToInvokeFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DaprConstants.METHOD_TO_INVOKE, String.class);
    }

    public static String getVerbFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DaprConstants.VERB, String.class);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, List<String>> getQueryParametersFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DaprConstants.QUERY_PARAMETERS, Map.class);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, String> getHttpHeadersFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DaprConstants.HTTP_HEADERS, Map.class);
    }

    public static HttpExtension getHttpExtensionFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DaprConstants.HTTP_EXTENSION, HttpExtension.class);
    }

    public static StateOperation getStateOperationFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DaprConstants.STATE_OPERATION, StateOperation.class);
    }

    public static String getStateStoreFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DaprConstants.STATE_STORE, String.class);
    }

    public static String getSecretStoreFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DaprConstants.SECRET_STORE, String.class);
    }

    public static String getConfigStoreFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DaprConstants.CONFIG_STORE, String.class);
    }

    public static String getKeyFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DaprConstants.KEY, String.class);
    }

    public static String getETagFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DaprConstants.E_TAG, String.class);
    }

    public static Concurrency getConcurrencyFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DaprConstants.CONCURRENCY, Concurrency.class);
    }

    public static Consistency getConsistencyFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DaprConstants.CONSISTENCY, Consistency.class);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, String> getMetadataFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DaprConstants.METADATA, Map.class);
    }

    @SuppressWarnings("unchecked")
    public static List<State<?>> getStatesFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DaprConstants.STATES, List.class);
    }

    @SuppressWarnings("unchecked")
    public static List<String> getKeysFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DaprConstants.KEYS, List.class);
    }

    @SuppressWarnings("unchecked")
    public static List<TransactionalStateOperation<?>> getTransactionsFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DaprConstants.TRANSACTIONS, List.class);
    }

    public static String getPubSubNameFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DaprConstants.PUBSUB_NAME, String.class);
    }

    public static String getTopicFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DaprConstants.TOPIC, String.class);
    }

    public static String getContentTypeFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DaprConstants.CONTENT_TYPE, String.class);
    }

    public static String getBindingNameFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DaprConstants.BINDING_NAME, String.class);
    }

    public static String getBindingOperationFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DaprConstants.BINDING_OPERATION, String.class);
    }

    @SuppressWarnings("unchecked")
    public static String getConfigKeysFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DaprConstants.CONFIG_KEYS, String.class);
    }

    public static LockOperation getLockOperationFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DaprConstants.LOCK_OPERATION, LockOperation.class);
    }

    public static String getStoreNameFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DaprConstants.STORE_NAME, String.class);
    }

    public static String getResourceIdFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DaprConstants.RESOURCE_ID, String.class);
    }

    public static String getLockOwnerFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DaprConstants.LOCK_OWNER, String.class);
    }

    public static Integer getExpiryInSecondsFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DaprConstants.EXPIRY_IN_SECONDS, Integer.class);
    }

    public static WorkflowOperation getWorkflowOperationFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DaprConstants.WORKFLOW_OPERATION, WorkflowOperation.class);
    }

    public static String getWorkflowClassFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DaprConstants.WORKFLOW_CLASS, String.class);
    }

    public static String getWorkflowVersionFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DaprConstants.WORKFLOW_VERSION, String.class);
    }

    public static String getWorkflowInstanceIdFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DaprConstants.WORKFLOW_INSTANCE_ID, String.class);
    }

    public static Instant getWorkflowStartTimeFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DaprConstants.WORKFLOW_START_TIME, Instant.class);
    }

    public static String getReasonFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DaprConstants.REASON, String.class);
    }

    public static boolean getWorkflowIOFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DaprConstants.GET_WORKFLOW_IO, boolean.class);
    }

    public static Duration getTimeoutFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DaprConstants.GET_WORKFLOW_IO, Duration.class);
    }

    public static String getEventNameFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, DaprConstants.EVENT_NAME, String.class);
    }

    private static <T> T getObjectFromHeaders(
            final Exchange exchange, final String headerName, final Class<T> classType) {
        return ObjectHelper.isEmpty(exchange) ? null : exchange.getIn().getHeader(headerName, classType);
    }
}
