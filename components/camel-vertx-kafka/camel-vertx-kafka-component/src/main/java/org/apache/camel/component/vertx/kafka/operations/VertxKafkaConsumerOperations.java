package org.apache.camel.component.vertx.kafka.operations;

import java.util.Collections;
import java.util.Set;
import java.util.function.Consumer;

import io.vertx.core.AsyncResult;
import io.vertx.kafka.client.common.TopicPartition;
import io.vertx.kafka.client.consumer.KafkaConsumer;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import org.apache.camel.component.vertx.kafka.configuration.VertxKafkaConfiguration;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

        // once the consumer has assigned partitions, we will attempt to seek in case conditions met
        seekOnPartitionAssignment(topicSubscription.getSeekToPosition(), topicSubscription.getSeekToOffset(), recordHandler,
                errorHandler);

        if (ObjectHelper.isEmpty(topicSubscription.getPartitionId())) {
            // we subscribe to all partitions if the user does not specify any particular partition to consume from
            subscribe(topicSubscription, recordHandler, errorHandler);
        } else {
            // else we have to assign to particular partition manually
        }
    }

    private void seekOnPartitionAssignment(
            final TopicSubscription.OffsetPosition position, final Long offset,
            final Consumer<KafkaConsumerRecord<Object, Object>> recordHandler,
            final Consumer<Throwable> errorHandler) {
        // seek if we have either position or offset
        if (ObjectHelper.isNotEmpty(offset) || ObjectHelper.isNotEmpty(position)) {
            // once we have our partitions assigned, we start to seek
            kafkaConsumer.partitionsAssignedHandler(partitions -> {
                partitions.forEach(topicPartition -> {
                    LOG.debug("Topic {} assigned to topic partition {}", topicPartition.getTopic(),
                            topicPartition.getPartition());
                    seekToOffsetInPartition(topicPartition, position, offset, recordHandler, errorHandler);
                });
            });
        }
    }

    private void seekToOffsetInPartition(
            final TopicPartition topicPartition, final TopicSubscription.OffsetPosition position, final Long offset,
            final Consumer<KafkaConsumerRecord<Object, Object>> recordHandler,
            final Consumer<Throwable> errorHandler) {
        if (ObjectHelper.isNotEmpty(offset)) {
            // if offset is set, then we seek the partition to that offset
            LOG.info("Seeking topic {} with partition {} to offset {}.", topicPartition.getTopic(),
                    topicPartition.getPartition(), offset);
            kafkaConsumer.seek(topicPartition, offset, result -> handleResult(result, recordHandler, errorHandler));
        } else {
            // we have position set here, let's handle this
            seekToPosition(topicPartition, position, recordHandler, errorHandler);
        }
    }

    private void seekToPosition(
            final TopicPartition topicPartition, final TopicSubscription.OffsetPosition position,
            final Consumer<KafkaConsumerRecord<Object, Object>> recordHandler,
            final Consumer<Throwable> errorHandler) {
        switch (position) {
            case BEGINNING:
                LOG.info("Seeking topic {} with partition {} to the beginning.", topicPartition.getTopic(),
                        topicPartition.getPartition());
                kafkaConsumer.seekToBeginning(topicPartition, result -> handleResult(result, recordHandler, errorHandler));
                break;

            case END:
                LOG.info("Seeking topic {} with partition {} to the end.", topicPartition.getTopic(),
                        topicPartition.getPartition());
                kafkaConsumer.seekToEnd(topicPartition, result -> handleResult(result, recordHandler, errorHandler));
                break;

            default:
                LOG.warn("No valid positions being set, hence the seeking operation will be ignored.");
        }
    }

    private void subscribe(
            final TopicSubscription topicSubscription, final Consumer<KafkaConsumerRecord<Object, Object>> recordHandler,
            final Consumer<Throwable> errorHandler) {
        // for now support we support single topic, however we can add set of topics as well as pattern assignment
        subscribeToTopics(Collections.singleton(topicSubscription.getTopicName()), recordHandler, errorHandler);
    }

    private void subscribeToTopics(
            final Set<String> topics, final Consumer<KafkaConsumerRecord<Object, Object>> recordHandler,
            final Consumer<Throwable> errorHandler) {
        kafkaConsumer.subscribe(topics, result -> handleResult(result, recordHandler, errorHandler));
    }

    private void handleResult(
            final AsyncResult<Void> result, final Consumer<KafkaConsumerRecord<Object, Object>> recordHandler,
            final Consumer<Throwable> errorHandler) {
        if (result.failed()) {
            // the operation is failed, we notify the error callback
            errorHandler.accept(result.cause());
        } else {
            // otherwise we handle our kafka records
            kafkaConsumer.handler(recordHandler::accept);
        }
    }
}
