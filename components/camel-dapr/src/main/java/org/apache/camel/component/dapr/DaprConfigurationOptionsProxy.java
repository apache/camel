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
import java.util.function.Function;
import java.util.function.Supplier;

import io.dapr.client.domain.HttpExtension;
import io.dapr.client.domain.State;
import io.dapr.client.domain.StateOptions.Concurrency;
import io.dapr.client.domain.StateOptions.Consistency;
import io.dapr.client.domain.TransactionalStateOperation;
import org.apache.camel.Exchange;
import org.apache.camel.util.ObjectHelper;

/**
 * A proxy class for {@link DaprConfigurationOptionsProxy} and {@link DaprExchangeHeaders}. Ideally this is responsible
 * to obtain the correct configurations options either from configs or exchange headers
 */
public class DaprConfigurationOptionsProxy {

    private final DaprConfiguration configuration;

    public DaprConfigurationOptionsProxy(final DaprConfiguration config) {
        this.configuration = config;
    }

    public DaprOperation getOperation() {
        return configuration.getOperation();
    }

    public String getServiceToInvoke(final Exchange exchange) {
        return getOption(DaprExchangeHeaders::getServiceToInvokeFromHeaders, configuration::getServiceToInvoke, exchange);
    }

    public String getMethodToInvoke(final Exchange exchange) {
        return getOption(DaprExchangeHeaders::getMethodToInvokeFromHeaders, configuration::getMethodToInvoke, exchange);
    }

    public String getVerb(final Exchange exchange) {
        return getOption(DaprExchangeHeaders::getVerbFromHeaders, configuration::getVerb, exchange);
    }

    public Map<String, List<String>> getQueryParameters(final Exchange exchange) {
        return getOption(DaprExchangeHeaders::getQueryParametersFromHeaders, () -> null, exchange);
    }

    public Map<String, String> getHttpHeaders(final Exchange exchange) {
        return getOption(DaprExchangeHeaders::getHttpHeadersFromHeaders, () -> null, exchange);
    }

    public HttpExtension getHttpExtension(final Exchange exchange) {
        return getOption(DaprExchangeHeaders::getHttpExtensionFromHeaders, configuration::getHttpExtension, exchange);
    }

    public StateOperation getStateOperation(final Exchange exchange) {
        return getOption(DaprExchangeHeaders::getStateOperationFromHeaders, configuration::getStateOperation, exchange);
    }

    public String getStateStore(final Exchange exchange) {
        return getOption(DaprExchangeHeaders::getStateStoreFromHeaders, configuration::getStateStore, exchange);
    }

    public String getKey(final Exchange exchange) {
        return getOption(DaprExchangeHeaders::getKeyFromHeaders, configuration::getKey, exchange);
    }

    public String getETag(final Exchange exchange) {
        return getOption(DaprExchangeHeaders::getETagFromHeaders, configuration::getETag, exchange);
    }

    public Concurrency getConcurrency(final Exchange exchange) {
        return getOption(DaprExchangeHeaders::getConcurrencyFromHeaders, configuration::getConcurrency, exchange);
    }

    public Consistency getConsistency(final Exchange exchange) {
        return getOption(DaprExchangeHeaders::getConsistencyFromHeaders, configuration::getConsistency, exchange);
    }

    public Map<String, String> getMetadata(final Exchange exchange) {
        return getOption(DaprExchangeHeaders::getMetadataFromHeaders, () -> null, exchange);
    }

    public List<State<?>> getStates(final Exchange exchange) {
        return getOption(DaprExchangeHeaders::getStatesFromHeaders, () -> null, exchange);
    }

    public List<String> getKeys(final Exchange exchange) {
        return getOption(DaprExchangeHeaders::getKeysFromHeaders, () -> null, exchange);
    }

    public List<TransactionalStateOperation<?>> getTransactions(final Exchange exchange) {
        return getOption(DaprExchangeHeaders::getTransactionsFromHeaders, () -> null, exchange);
    }

    private <R> R getOption(final Function<Exchange, R> exchangeFn, final Supplier<R> fallbackFn, final Exchange exchange) {
        // we first try to look if our value in exchange otherwise fallback to fallbackFn which could be either a function or constant
        return ObjectHelper.isEmpty(exchange) || ObjectHelper.isEmpty(exchangeFn.apply(exchange))
                ? fallbackFn.get()
                : exchangeFn.apply(exchange);
    }

}
