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

    private static <T> T getObjectFromHeaders(final Exchange exchange, final String headerName, final Class<T> classType) {
        return ObjectHelper.isEmpty(exchange) ? null : exchange.getIn().getHeader(headerName, classType);
    }
}
