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

    private <R> R getOption(final Function<Exchange, R> exchangeFn, final Supplier<R> fallbackFn, final Exchange exchange) {
        // we first try to look if our value in exchange otherwise fallback to fallbackFn which could be either a function or constant
        return ObjectHelper.isEmpty(exchange) || ObjectHelper.isEmpty(exchangeFn.apply(exchange))
                ? fallbackFn.get()
                : exchangeFn.apply(exchange);
    }

}
