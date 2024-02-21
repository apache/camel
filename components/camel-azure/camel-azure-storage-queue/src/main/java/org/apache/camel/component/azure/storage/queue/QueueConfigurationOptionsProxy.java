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
package org.apache.camel.component.azure.storage.queue;

import java.time.Duration;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import com.azure.storage.queue.models.QueuesSegmentOptions;
import org.apache.camel.Exchange;
import org.apache.camel.util.ObjectHelper;

/**
 * A proxy class for {@link QueueConfiguration} and {@link QueueExchangeHeaders}. Ideally this is responsible to obtain
 * the correct configurations options either from configs or exchange headers
 */
public class QueueConfigurationOptionsProxy {

    private final QueueConfiguration configuration;

    public QueueConfigurationOptionsProxy(final QueueConfiguration configuration) {
        this.configuration = configuration;
    }

    public Duration getTimeout(final Exchange exchange) {
        return getOption(QueueExchangeHeaders::getTimeoutFromHeaders, configuration::getTimeout, exchange);
    }

    public Duration getVisibilityTimeout(final Exchange exchange) {
        return getOption(QueueExchangeHeaders::getVisibilityTimeout, configuration::getVisibilityTimeout, exchange);
    }

    public Duration getTimeToLive(final Exchange exchange) {
        return getOption(QueueExchangeHeaders::getTimeToLiveFromHeaders, configuration::getTimeToLive, exchange);
    }

    public Integer getMaxMessages(final Exchange exchange) {
        return getOption(QueueExchangeHeaders::getMaxMessagesFromHeaders, configuration::getMaxMessages, exchange);
    }

    public String getQueueName(final Exchange exchange) {
        return getOption(QueueExchangeHeaders::getQueueNameFromHeaders, configuration::getQueueName, exchange);
    }

    public Map<String, String> getMetadata(final Exchange exchange) {
        return QueueExchangeHeaders.getMetadataFromHeaders(exchange);
    }

    public String getMessageId(final Exchange exchange) {
        return getOption(QueueExchangeHeaders::getMessageIdFromHeaders, configuration::getMessageId, exchange);
    }

    public String getPopReceipt(final Exchange exchange) {
        return getOption(QueueExchangeHeaders::getPopReceiptFromHeaders, configuration::getPopReceipt, exchange);
    }

    public QueuesSegmentOptions getQueuesSegmentOptions(final Exchange exchange) {
        return QueueExchangeHeaders.getQueuesSegmentOptionsFromHeaders(exchange);
    }

    public boolean isCreateQueue(final Exchange exchange) {
        return getOption(QueueExchangeHeaders::getCreateQueueFlagFromHeaders, configuration::isCreateQueue, exchange);
    }

    public QueueOperationDefinition getQueueOperation(final Exchange exchange) {
        return getOption(QueueExchangeHeaders::getQueueOperationsDefinitionFromHeaders, configuration::getOperation, exchange);
    }

    public QueueConfiguration getConfiguration() {
        return configuration;
    }

    private <R> R getOption(final Function<Exchange, R> exchangeFn, final Supplier<R> fallbackFn, final Exchange exchange) {
        // we first try to look if our value in exchange otherwise fallback to fallbackFn which could be either a function or constant
        return ObjectHelper.isEmpty(exchange) || ObjectHelper.isEmpty(exchangeFn.apply(exchange))
                ? fallbackFn.get()
                : exchangeFn.apply(exchange);
    }
}
