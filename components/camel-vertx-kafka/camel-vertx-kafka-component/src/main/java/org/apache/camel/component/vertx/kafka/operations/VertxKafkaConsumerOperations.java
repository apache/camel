package org.apache.camel.component.vertx.kafka.operations;

import java.util.Collections;
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
            throw new IllegalArgumentException("Topic, list of topics or topic pattern needs to be set in the topic config.");
        }

        final TopicSubscription topicSubscription = new TopicSubscription(
                configuration.getTopic(), configuration.getPartitionId(), configuration.getSeekToOffset(),
                configuration.getSeekToPosition());

        // register our record handler
        kafkaConsumer.handler(recordHandler::accept);

        // once the consumer has assigned partitions, we will attempt to seek in case conditions met
        // TODO: this is wrong, we seek only if we store our offsets, hence we need to change this to only see upon starting
        seekOnPartitionAssignment(topicSubscription, errorHandler);

        if (ObjectHelper.isEmpty(topicSubscription.getPartitionId())) {
            // we subscribe to all partitions if the user does not specify any particular partition to consume from
            subscribeToTopics(topicSubscription, errorHandler);
        } else {
            // else we have to assign to particular partition manually
        }
    }

    private void seekOnPartitionAssignment(
            final TopicSubscription topicSubscription, final Consumer<Throwable> errorHandler) {
        // seek if we have either position or offset
        if (ObjectHelper.isNotEmpty(topicSubscription.getSeekToOffset()) || ObjectHelper.isNotEmpty(topicSubscription.getSeekToPosition())) {
            // once we have our partitions assigned, we start to seek
            /*onPartitionAssignment()
                    .flatMap(topicPartition -> seekToOffsetOrPositionInPartition(topicPartition, topicSubscription))
                    .subscribe(result -> {}, errorHandler, () -> {});*/
        }
    }

    private Flux<TopicPartition> onPartitionAssignment() {
        return Flux.create(sink -> kafkaConsumer.partitionsAssignedHandler(partitions -> {
            LOG.info("Partition {} is assigned to consumer", partitions);
            partitions.forEach(topicPartition -> {
                LOG.info("Partition {} is assigned to consumer for topic {}", topicPartition.getPartition(), topicPartition.getTopic());
                sink.next(topicPartition);
            });
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

    private void subscribeToTopics(
            final TopicSubscription topicSubscription, final Consumer<Throwable> errorHandler) {
        // for now support we support single topic, however we can add set of topics as well as pattern assignment
        subscribeToTopics(Collections.singleton(topicSubscription.getTopicName()))
                .onErrorResume(Mono::error)
                .subscribe((unused) -> {}, errorHandler, () -> {});
    }

    private Mono<Void> subscribeToTopics(final Set<String> topics) {
        return Mono.create(sink -> kafkaConsumer.subscribe(topics, result -> {
            if (result.failed()) {
                sink.error(result.cause());
            } else {
                sink.success();
            }
        }));
    }

    private <R> Mono<R> wrapResultToMono(final Consumer<Handler<R>> fn) {
        return Mono.create(sink -> fn.accept(sink::success));
    }

    private <R> Mono<R> wrapToMono(final Consumer<Handler<AsyncResult<R>>> fn) {
        return Mono.create(sink -> fn.accept(result -> wrapAsyncResult(sink, result)));
    }

    private <R, V> Mono<R> wrapToMono(final BiConsumer<V, Handler<AsyncResult<R>>> fn, final V input) {
        return Mono.create(sink -> fn.accept(input, result -> wrapAsyncResult(sink, result)));
    }

    private <R, V1, V2> Mono<R> wrapToMono(final TriConsumer<V1, V2, Handler<AsyncResult<R>>> fn, final V1 input1, final V2 input2) {
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
