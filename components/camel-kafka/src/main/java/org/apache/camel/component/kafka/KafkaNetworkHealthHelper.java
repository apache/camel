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
package org.apache.camel.component.kafka;

import org.apache.camel.util.ReflectionHelper;
import org.apache.kafka.clients.KafkaClient;
import org.apache.kafka.clients.NetworkClient;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.internals.ConsumerNetworkClient;
import org.apache.kafka.clients.producer.internals.Sender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves Kafka client network readiness across classic and async consumer protocols.
 * <p/>
 * Uses reflection against kafka-clients internals (same approach as CAMEL-20592) because the public consumer/producer
 * APIs do not expose connectivity state.
 */
final class KafkaNetworkHealthHelper {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaNetworkHealthHelper.class);

    private KafkaNetworkHealthHelper() {
    }

    static boolean consumerHasReadyNodes(Consumer<?, ?> consumer) {
        if (!(consumer instanceof org.apache.kafka.clients.consumer.KafkaConsumer<?, ?> kafkaConsumer)) {
            // Custom consumer implementations keep legacy fail-open behavior
            return true;
        }
        try {
            Object delegate = ReflectionHelper.getField(kafkaConsumer.getClass().getDeclaredField("delegate"), kafkaConsumer);
            if (delegate == null) {
                LOG.warn("KafkaConsumer delegate is null; treating consumer readiness as not ready");
                return false;
            }
            long now = System.currentTimeMillis();

            Boolean classicReady = classicConsumerHasReadyNodes(delegate, now);
            if (classicReady != null) {
                return classicReady;
            }

            Boolean asyncReady = asyncConsumerHasReadyNodes(delegate, now);
            if (asyncReady != null) {
                return asyncReady;
            }

            LOG.warn("Cannot resolve Kafka consumer network client for readiness check; treating as not ready");
            return false;
        } catch (Exception e) {
            LOG.debug("Cannot check hasReadyNodes on KafkaConsumer due to: {}. Treating as not ready.", e.getMessage(), e);
            return false;
        }
    }

    static boolean producerHasReadyNodes(org.apache.kafka.clients.producer.Producer<?, ?> producer) {
        if (!(producer instanceof org.apache.kafka.clients.producer.KafkaProducer<?, ?> kafkaProducer)) {
            // Custom producer implementations keep legacy fail-open behavior
            return true;
        }
        try {
            Sender sender
                    = (Sender) ReflectionHelper.getField(kafkaProducer.getClass().getDeclaredField("sender"), kafkaProducer);
            if (sender == null) {
                return true;
            }
            NetworkClient networkClient
                    = (NetworkClient) ReflectionHelper.getField(sender.getClass().getDeclaredField("client"), sender);
            if (networkClient == null) {
                return true;
            }
            LOG.trace("Health-Check calling NetworkClient.hasReadyNodes");
            return networkClient.hasReadyNodes(System.currentTimeMillis());
        } catch (Exception e) {
            LOG.debug("Cannot check hasReadyNodes on KafkaProducer due to: {}. This exception is ignored.", e.getMessage(), e);
            return true;
        }
    }

    private static Boolean classicConsumerHasReadyNodes(Object delegate, long now) throws Exception {
        try {
            Object client = ReflectionHelper.getField(delegate.getClass().getDeclaredField("client"), delegate);
            if (client instanceof ConsumerNetworkClient networkClient) {
                LOG.trace("Health-Check calling ConsumerNetworkClient.hasReadyNodes");
                return networkClient.hasReadyNodes(now);
            }
            if (client instanceof KafkaClient kafkaClient) {
                return kafkaClient.hasReadyNodes(now);
            }
        } catch (NoSuchFieldException e) {
            // not classic layout
        }
        return null;
    }

    private static Boolean asyncConsumerHasReadyNodes(Object delegate, long now) throws Exception {
        try {
            Object applicationEventHandler
                    = ReflectionHelper.getField(delegate.getClass().getDeclaredField("applicationEventHandler"), delegate);
            if (applicationEventHandler == null) {
                return null;
            }
            Object networkThread = ReflectionHelper.getField(
                    applicationEventHandler.getClass().getDeclaredField("networkThread"), applicationEventHandler);
            if (networkThread == null) {
                return false;
            }
            Object networkClientDelegate = ReflectionHelper.getField(
                    networkThread.getClass().getDeclaredField("networkClientDelegate"), networkThread);
            if (networkClientDelegate == null) {
                return false;
            }
            KafkaClient kafkaClient = (KafkaClient) ReflectionHelper.getField(
                    networkClientDelegate.getClass().getDeclaredField("client"), networkClientDelegate);
            if (kafkaClient == null) {
                return false;
            }
            LOG.trace("Health-Check calling KafkaClient.hasReadyNodes on async consumer delegate");
            return kafkaClient.hasReadyNodes(now);
        } catch (NoSuchFieldException e) {
            return null;
        }
    }
}
