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
package org.apache.camel.component.vertx.kafka.operations;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

import io.vertx.kafka.client.producer.KafkaHeader;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.kafka.client.producer.KafkaProducerRecord;
import io.vertx.kafka.client.producer.RecordMetadata;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.vertx.kafka.VertxKafkaConfigurationOptionsProxy;
import org.apache.camel.component.vertx.kafka.VertxKafkaConstants;
import org.apache.camel.component.vertx.kafka.VertxKafkaHeadersPropagation;
import org.apache.camel.component.vertx.kafka.configuration.VertxKafkaConfiguration;
import org.apache.camel.component.vertx.kafka.serde.VertxKafkaTypeSerializer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class VertxKafkaProducerOperations {

    private static final Logger LOG = LoggerFactory.getLogger(VertxKafkaProducerOperations.class);

    private final KafkaProducer<Object, Object> kafkaProducer;
    private final VertxKafkaConfigurationOptionsProxy configurationOptionsProxy;

    public VertxKafkaProducerOperations(final KafkaProducer<Object, Object> kafkaProducer,
                                        final VertxKafkaConfiguration configuration) {
        ObjectHelper.notNull(kafkaProducer, "kafkaProducer");
        ObjectHelper.notNull(configuration, "configuration");

        this.kafkaProducer = kafkaProducer;
        configurationOptionsProxy = new VertxKafkaConfigurationOptionsProxy(configuration);
    }

    public boolean sendEvents(final Message inMessage, final AsyncCallback callback) {
        return sendEvents(inMessage, unused -> LOG.debug("Processed one event..."), callback);
    }

    public boolean sendEvents(
            final Message inMessage, final Consumer<List<RecordMetadata>> resultCallback, final AsyncCallback callback) {
        sendAsyncEvents(inMessage)
                .subscribe(resultCallback, error -> {
                    // error but we continue
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Error processing async exchange with error: {}", error.getMessage());
                    }
                    inMessage.getExchange().setException(error);
                    callback.done(false);
                }, () -> {
                    // we are done from everything, so mark it as sync done
                    LOG.trace("All events with exchange have been sent successfully.");
                    callback.done(false);
                });

        return false;
    }

    private Mono<List<RecordMetadata>> sendAsyncEvents(final Message inMessage) {
        return Flux.fromIterable(createKafkaProducerRecords(inMessage))
                .flatMap(this::sendDataToKafka)
                .collectList()
                .doOnError(error -> LOG.error(error.getMessage()));
    }

    private Mono<RecordMetadata> sendDataToKafka(final KafkaProducerRecord<Object, Object> producerRecord) {
        return Mono.create(sink -> kafkaProducer.send(producerRecord, asyncResult -> {
            if (asyncResult.failed()) {
                sink.error(asyncResult.cause());
            } else {
                sink.success(asyncResult.result());
            }
        }));
    }

    @SuppressWarnings("unchecked")
    private Iterable<KafkaProducerRecord<Object, Object>> createKafkaProducerRecords(final Message inMessage) {
        // check if our exchange is list or contain some values
        if (inMessage.getBody() instanceof Iterable) {
            return createProducerRecordFromIterable((Iterable<Object>) inMessage.getBody(), inMessage);
        }

        // we have only a single event here
        return Collections.singletonList(createProducerRecordFromMessage(inMessage, null));
    }

    private Iterable<KafkaProducerRecord<Object, Object>> createProducerRecordFromIterable(
            final Iterable<Object> inputData, final Message message) {
        final List<KafkaProducerRecord<Object, Object>> finalRecords = new LinkedList<>();

        final String parentTopic = getTopic(message, null);

        inputData.forEach(data -> {
            if (data instanceof Exchange) {
                finalRecords.add(createProducerRecordFromExchange((Exchange) data, parentTopic));
            } else if (data instanceof Message) {
                finalRecords.add(createProducerRecordFromMessage((Message) data, parentTopic));
            } else {
                finalRecords.add(createProducerRecordFromObject(data, message, parentTopic));
            }
        });

        return finalRecords;
    }

    private KafkaProducerRecord<Object, Object> createProducerRecordFromExchange(
            final Exchange exchange, final String parentTopic) {
        return createProducerRecordFromMessage(exchange.getIn(), parentTopic);
    }

    private KafkaProducerRecord<Object, Object> createProducerRecordFromMessage(
            final Message message, final String parentTopic) {
        return createProducerRecordFromObject(message.getBody(), message, parentTopic);
    }

    private KafkaProducerRecord<Object, Object> createProducerRecordFromObject(
            final Object inputData, final Message message, final String parentTopic) {

        final String topic = getTopic(message, parentTopic);
        final Object messageKey = getMessageKey(message);
        final Object messageValue = getMessageValue(message, inputData);
        final Integer partitionId = getPartitionId(message);
        final Long overrideTimestamp = getOverrideTimestamp(message);
        final List<KafkaHeader> propagatedHeaders
                = new VertxKafkaHeadersPropagation(configurationOptionsProxy.getConfiguration().getHeaderFilterStrategy())
                        .getPropagatedHeaders(message);

        return KafkaProducerRecord.create(topic, messageKey, messageValue, overrideTimestamp, partitionId)
                .addHeaders(propagatedHeaders);
    }

    private String getTopic(final Message message, final String parentTopic) {
        final String innerOverrideTopic = configurationOptionsProxy.getOverrideTopic(message);
        final String innerMessageTopic = message.getHeader(VertxKafkaConstants.TOPIC, String.class);

        final String topic = getTopic(message, innerOverrideTopic, innerMessageTopic, parentTopic);

        if (ObjectHelper.isEmpty(topic)) {
            throw new IllegalArgumentException("Topic cannot be empty, provide a topic in the config or in the headers.");
        }

        return topic;
    }

    private String getTopic(
            final Message message, final String innerOverrideTopic, final String innerTopic, final String parentTopic) {
        // first check if we have override topic on inner message otherwise fall to innerTopic
        // second check if we have a innerTopic on inner message otherwise fall to parentTopic (from the main exchange)
        // third check if we have a parent topic (set in the headers of TOPIC) in the main exchange otherwise fall to config
        final String firstCheckStep = ObjectHelper.isEmpty(innerOverrideTopic) ? innerTopic : innerOverrideTopic;
        final String secondCheckStep = ObjectHelper.isEmpty(firstCheckStep) ? parentTopic : firstCheckStep;

        // third check step
        return ObjectHelper.isEmpty(secondCheckStep) ? configurationOptionsProxy.getTopic(message) : secondCheckStep;
    }

    private Object getMessageKey(final Message message) {
        return VertxKafkaTypeSerializer.tryConvertToSerializedType(message,
                configurationOptionsProxy.getMessageKey(message),
                configurationOptionsProxy.getKeySerializer());
    }

    private Integer getPartitionId(final Message message) {
        return configurationOptionsProxy.getPartitionId(message);
    }

    private Object getMessageValue(final Message message, final Object inputData) {
        return VertxKafkaTypeSerializer.tryConvertToSerializedType(message, inputData,
                configurationOptionsProxy.getValueSerializer());
    }

    private Long getOverrideTimestamp(final Message message) {

        Object timeStamp = configurationOptionsProxy.getOverrideTimestamp(message);
        Long overrideTimestamp = null;
        if (ObjectHelper.isNotEmpty(timeStamp)) {
            overrideTimestamp = (Long) timeStamp;
        }

        return overrideTimestamp;

    }

}
