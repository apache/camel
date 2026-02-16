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
package org.apache.camel.component.azure.functions;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.apache.camel.Exchange;
import org.apache.camel.util.ObjectHelper;

/**
 * Proxy for accessing configuration options with support for header overrides.
 */
public class FunctionsConfigurationOptionsProxy {

    private final FunctionsConfiguration configuration;

    public FunctionsConfigurationOptionsProxy(FunctionsConfiguration configuration) {
        this.configuration = configuration;
    }

    public FunctionsConfiguration getConfiguration() {
        return configuration;
    }

    public FunctionsOperations getOperation(Exchange exchange) {
        return getOption(exchange, FunctionsConstants.OPERATION,
                configuration::getOperation, FunctionsOperations.class);
    }

    public String getFunctionApp(Exchange exchange) {
        return getOption(exchange, FunctionsConstants.FUNCTION_APP,
                configuration::getFunctionApp, String.class);
    }

    public String getFunctionName(Exchange exchange) {
        return getOption(exchange, FunctionsConstants.FUNCTION_NAME,
                configuration::getFunctionName, String.class);
    }

    public String getResourceGroup(Exchange exchange) {
        return getOption(exchange, FunctionsConstants.RESOURCE_GROUP,
                configuration::getResourceGroup, String.class);
    }

    public String getHttpMethod(Exchange exchange) {
        return getOption(exchange, FunctionsConstants.HTTP_METHOD,
                configuration::getHttpMethod, String.class);
    }

    @SuppressWarnings("unchecked")
    public Map<String, String> getHttpHeaders(Exchange exchange) {
        return getOption(exchange, FunctionsConstants.HTTP_HEADERS,
                () -> null, Map.class);
    }

    public String getLocation(Exchange exchange) {
        return getOption(exchange, FunctionsConstants.LOCATION,
                configuration::getLocation, String.class);
    }

    public String getRuntime(Exchange exchange) {
        return getOption(exchange, FunctionsConstants.RUNTIME,
                configuration::getRuntime, String.class);
    }

    public String getRuntimeVersion(Exchange exchange) {
        return getOption(exchange, FunctionsConstants.RUNTIME_VERSION,
                configuration::getRuntimeVersion, String.class);
    }

    public String getStorageConnectionString(Exchange exchange) {
        return getOption(exchange, FunctionsConstants.STORAGE_CONNECTION_STRING,
                configuration::getStorageAccountConnectionString, String.class);
    }

    @SuppressWarnings("unchecked")
    public Map<String, String> getAppSettings(Exchange exchange) {
        return getOption(exchange, FunctionsConstants.APP_SETTINGS,
                () -> null, Map.class);
    }

    @SuppressWarnings("unchecked")
    public Map<String, String> getResourceTags(Exchange exchange) {
        return getOption(exchange, FunctionsConstants.RESOURCE_TAGS,
                () -> null, Map.class);
    }

    @SuppressWarnings("unchecked")
    public List<String> getTagKeys(Exchange exchange) {
        return getOption(exchange, FunctionsConstants.TAG_KEYS,
                () -> null, List.class);
    }

    private <T> T getOption(Exchange exchange, String headerName, Supplier<T> fallbackFn, Class<T> type) {
        if (ObjectHelper.isEmpty(exchange)) {
            return fallbackFn.get();
        }
        T headerValue = exchange.getIn().getHeader(headerName, type);
        return ObjectHelper.isEmpty(headerValue) ? fallbackFn.get() : headerValue;
    }
}
