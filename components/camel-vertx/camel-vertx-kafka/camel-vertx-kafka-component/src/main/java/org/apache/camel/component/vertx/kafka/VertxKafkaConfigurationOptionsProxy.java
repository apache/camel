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
package org.apache.camel.component.vertx.kafka;

import java.util.function.Supplier;

import org.apache.camel.Message;
import org.apache.camel.component.vertx.kafka.configuration.VertxKafkaConfiguration;
import org.apache.camel.util.ObjectHelper;

public class VertxKafkaConfigurationOptionsProxy {

    private final VertxKafkaConfiguration configuration;

    public VertxKafkaConfigurationOptionsProxy(final VertxKafkaConfiguration configuration) {
        this.configuration = configuration;
    }

    public Integer getPartitionId(final Message message) {
        return getOption(message, VertxKafkaConstants.PARTITION_ID, configuration::getPartitionId, Integer.class);
    }

    public Object getMessageKey(final Message message) {
        return getOption(message, VertxKafkaConstants.MESSAGE_KEY, () -> null, Object.class);
    }

    public String getKeySerializer() {
        return configuration.getKeySerializer();
    }

    public String getValueSerializer() {
        return configuration.getValueSerializer();
    }

    public Object getOverrideTimestamp(final Message message) {
        Object timestamp = getOption(message, VertxKafkaConstants.OVERRIDE_TIMESTAMP, () -> null, Object.class);
        if (ObjectHelper.isNotEmpty(timestamp)) {
            // must remove header so its not propagated
            message.removeHeader(VertxKafkaConstants.OVERRIDE_TIMESTAMP);
        }

        return timestamp;

    }

    public String getTopic(final Message message) {
        return getOption(message, VertxKafkaConstants.TOPIC, configuration::getTopic, String.class);
    }

    public String getOverrideTopic(final Message message) {
        final String topic = getOption(message, VertxKafkaConstants.OVERRIDE_TOPIC, () -> null, String.class);
        if (ObjectHelper.isNotEmpty(topic)) {
            // must remove header so its not propagated
            message.removeHeader(VertxKafkaConstants.OVERRIDE_TOPIC);
        }

        return topic;
    }

    public VertxKafkaConfiguration getConfiguration() {
        return configuration;
    }

    private <R> R getOption(
            final Message message, final String headerName, final Supplier<R> fallbackFn, final Class<R> type) {
        // we first try to look if our value in exchange otherwise fallback to fallbackFn which could be either a function or constant
        return ObjectHelper.isEmpty(message) || ObjectHelper.isEmpty(getObjectFromHeaders(message, headerName, type))
                ? fallbackFn.get()
                : getObjectFromHeaders(message, headerName, type);
    }

    private <T> T getObjectFromHeaders(final Message message, final String headerName, final Class<T> classType) {
        return message.getHeader(headerName, classType);
    }
}
