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

import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.kafka.client.common.TopicPartition;
import io.vertx.kafka.client.consumer.KafkaConsumer;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import org.apache.camel.component.vertx.kafka.configuration.VertxKafkaConfiguration;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.function.TriConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;

public class VertxKafkaConsumerOperations {

    private static final Logger LOG = LoggerFactory.getLogger(VertxKafkaConsumerOperations.class);

    private final KafkaConsumer<Object, Object> kafkaConsumer;
    private final VertxKafkaConfiguration configuration;

    public VertxKafkaConsumerOperations(final KafkaConsumer<Object, Object> kafkaConsumer,
                                        final VertxKafkaConfiguration configuration) {
        this.kafkaConsumer = kafkaConsumer;
        this.configuration = configuration;
    }

    public void receiveEvents(
            final Consumer<KafkaConsumerRecord<Object, Object>> recordHandler, final Consumer<Throwable> errorHandler) {

        if (ObjectHelper.isEmpty(configuration.getTopic())) {
            throw new IllegalArgumentException("Topic or list of topics need to be set in the topic config.");
        }

        final TopicSubscription topicSubscription = new TopicSubscription(
                configuration.getTopic(), configuration.getPartitionId(), configuration.getSeekToOffset(),
                configuration.getSeekToPosition());

        // register our record handler
        kafkaConsumer.handler(recordHandler::accept);

        // register our exception handler
        kafkaConsumer.exceptionHandler(errorHandler::accept);

        if (ObjectHelper.isEmpty(topicSubscription.getPartitionId())) {
            // we subscribe to all partitions if the user does not specify any particular partition to consume from
            subscribe(topicSubscription, errorHandler);
        } else {
            // else we have to assign to particular partition manually
            assign(topicSubscription, errorHandler);
        }
    }

    private void subscribe(
            final TopicSubscription topicSubscription, final Consumer<Throwable> errorHandler) {
        LOG.info("Subscribing to {} topics", topicSubscription.getConfiguredTopicName());
        // once the consumer has assigned partitions on startup, we will attempt to seek, we just register the handler before
        // since we use on assigment handler
        seekOnPartitionAssignment(topicSubscription, errorHandler);

        subscribeToTopics(topicSubscription.getTopics())
                .subscribe(unused -> {
                }, errorHandler, () -> {
                });
    }

    private void seekOnPartitionAssignment(
            final TopicSubscription topicSubscription, final Consumer<Throwable> errorHandler) {
        // seek if we have either position
        if (isSeekToSet(topicSubscription)) {
            // once we have our partitions assigned, we start to seek
            getTopicPartitionsOnPartitionAssignment()
                    .flatMap(topicPartition -> seekToOffsetOrPositionInPartition(topicPartition, topicSubscription))
                    .subscribe(result -> {
                    }, errorHandler, () -> LOG.info("Seeking partitions is done."));
        }
    }

    private boolean isSeekToSet(final TopicSubscription topicSubscription) {
        return ObjectHelper.isNotEmpty(topicSubscription.getSeekToOffset())
                || ObjectHelper.isNotEmpty(topicSubscription.getSeekToPosition());
    }

    private Flux<TopicPartition> getTopicPartitionsOnPartitionAssignment() {
        return Flux.create(sink -> kafkaConsumer.partitionsAssignedHandler(partitions -> {
            LOG.info("Partition {} is assigned to consumer", partitions);
            partitions.forEach(topicPartition -> {
                LOG.info("Partition {} is assigned to consumer for topic {}", topicPartition.getPartition(),
                        topicPartition.getTopic());
                sink.next(topicPartition);
            });
            // make sure we complete all partitions so it only happens once
            sink.complete();
        }));
    }

    private Mono<Void> seekToOffsetOrPositionInPartition(
            final TopicPartition topicPartition, final TopicSubscription topicSubscription) {
        if (ObjectHelper.isNotEmpty(topicSubscription.getSeekToOffset())) {
            // if offset is set, then we seek the partition to that offset
            LOG.info("Seeking topic {} with partition {} to offset {}.", topicPartition.getTopic(),
                    topicPartition.getPartition(), topicSubscription.getSeekToOffset());
            return wrapToMono(kafkaConsumer::seek, topicPartition, topicSubscription.getSeekToOffset());
        } else {
            // we have position set here, let's handle this
            return seekToPosition(topicPartition, topicSubscription.getSeekToPosition());
        }
    }

    private Mono<Void> seekToPosition(
            final TopicPartition topicPartition, final TopicSubscription.OffsetPosition position) {
        switch (position) {
            case BEGINNING:
                LOG.info("Seeking topic {} with partition {} to the beginning.", topicPartition.getTopic(),
                        topicPartition.getPartition());
                return wrapToMono(kafkaConsumer::seekToBeginning, topicPartition);

            case END:
                LOG.info("Seeking topic {} with partition {} to the end.", topicPartition.getTopic(),
                        topicPartition.getPartition());
                return wrapToMono(kafkaConsumer::seekToEnd, topicPartition);

            default:
                LOG.warn("No valid positions being set, hence the seeking operation will be ignored.");
                return Mono.empty();
        }
    }

    private Mono<Void> subscribeToTopics(final Set<String> topics) {
        return wrapToMono(kafkaConsumer::subscribe, topics);
    }

    private void assign(final TopicSubscription topicSubscription, final Consumer<Throwable> errorHandler) {
        LOG.info("Assigning topics {} to partition {}", topicSubscription.getConfiguredTopicName(),
                topicSubscription.getPartitionId());

        assignToPartitions(topicSubscription.getTopicPartitions())
                // once we have successfully assigned our partition, we proceed to seek in case we have conditions met
                .then(seekPartitionsManually(topicSubscription))
                .subscribe(unused -> {
                }, errorHandler, () -> {
                });
    }

    private Mono<Void> seekPartitionsManually(final TopicSubscription topicSubscription) {
        // seek if we have either position
        if (isSeekToSet(topicSubscription)) {
            return Flux.fromIterable(topicSubscription.getTopicPartitions())
                    .flatMap(topicPartition -> seekToOffsetOrPositionInPartition(topicPartition, topicSubscription))
                    .doOnComplete(() -> LOG.info("Seeking partitions is done."))
                    .then();
        }
        return Mono.empty();
    }

    private Mono<Void> assignToPartitions(final Set<TopicPartition> topicPartitions) {
        return wrapToMono(kafkaConsumer::assign, topicPartitions);
    }

    private <R, V> Mono<R> wrapToMono(final BiConsumer<V, Handler<AsyncResult<R>>> fn, final V input) {
        return Mono.create(sink -> fn.accept(input, result -> wrapAsyncResult(sink, result)));
    }

    private <R, V1, V2> Mono<R> wrapToMono(
            final TriConsumer<V1, V2, Handler<AsyncResult<R>>> fn, final V1 input1, final V2 input2) {
        return Mono.create(sink -> fn.accept(input1, input2, result -> wrapAsyncResult(sink, result)));
    }

    private <R> void wrapAsyncResult(final MonoSink<R> sink, final AsyncResult<R> result) {
        if (result.failed()) {
            sink.error(result.cause());
        } else {
            sink.success(result.result());
        }
    }
}
