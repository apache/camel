package org.apache.camel.component.vertx.kafka.operations;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.kafka.client.producer.KafkaProducerRecord;
import io.vertx.kafka.client.producer.RecordMetadata;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.vertx.kafka.VertxKafkaConfigurationOptionsProxy;
import org.apache.camel.component.vertx.kafka.VertxKafkaTypeConverter;
import org.apache.camel.component.vertx.kafka.configuration.VertxKafkaConfiguration;
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

    public boolean sendEvents(final Exchange exchange, final AsyncCallback callback) {
        ObjectHelper.notNull(exchange, "exchange cannot be null");
        ObjectHelper.notNull(callback, "callback cannot be null");

        return sendEvents(exchange, unused -> LOG.debug("Processed one event..."), callback);
    }

    public boolean sendEvents(
            final Exchange exchange, final Consumer<List<RecordMetadata>> resultCallback, final AsyncCallback callback) {
        ObjectHelper.notNull(exchange, "exchange cannot be null");
        ObjectHelper.notNull(callback, "callback cannot be null");

        sendAsyncEvents(exchange)
                .subscribe(resultCallback, error -> {
                    // error but we continue
                    LOG.debug("Error processing async exchange with error:" + error.getMessage());
                    exchange.setException(error);
                    callback.done(false);
                }, () -> {
                    // we are done from everything, so mark it as sync done
                    LOG.debug("All events with exchange have been sent successfully.");
                    callback.done(false);
                });

        return false;
    }

    private Mono<List<RecordMetadata>> sendAsyncEvents(final Exchange exchange) {
        return Flux.fromIterable(createKafkaProducerRecords(exchange))
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
    private Iterable<KafkaProducerRecord<Object, Object>> createKafkaProducerRecords(final Exchange exchange) {
        final String overrideTopic = configurationOptionsProxy.getOverrideTopic(exchange);
        final String topic = ObjectHelper.isEmpty(overrideTopic) ? configurationOptionsProxy.getTopic(exchange) : overrideTopic;

        // check if our exchange is list or contain some values
        if (exchange.getIn().getBody() instanceof Iterable) {
            return createProducerRecordFromIterable((Iterable<Object>) exchange.getIn().getBody(), topic, exchange);
        }

        // we have only a single event here
        return Collections.singletonList(createProducerRecordFromExchange(exchange, topic));
    }

    private Iterable<KafkaProducerRecord<Object, Object>> createProducerRecordFromIterable(
            final Iterable<Object> inputData, final String topic, final Exchange exchange) {
        final List<KafkaProducerRecord<Object, Object>> finalRecords = new LinkedList<>();

        inputData.forEach(data -> {
            if (data instanceof Exchange) {
                finalRecords.add(createProducerRecordFromExchange((Exchange) data, topic));
            } else if (data instanceof Message) {
                finalRecords.add(createProducerRecordFromMessage((Message) data, topic, exchange));
            } else {
                finalRecords.add(createProducerRecordFromObject(data, topic, exchange));
            }
        });

        return finalRecords;
    }

    private KafkaProducerRecord<Object, Object> createProducerRecordFromExchange(final Exchange exchange, final String topic) {
        return createProducerRecordFromMessage(exchange.getIn(), topic, exchange);
    }

    private KafkaProducerRecord<Object, Object> createProducerRecordFromMessage(
            final Message message, final String topic, final Exchange exchange) {
        return createProducerRecordFromObject(message.getBody(), topic, exchange);
    }

    private KafkaProducerRecord<Object, Object> createProducerRecordFromObject(
            final Object inputData, final String topic, final Exchange exchange) {
        final Object messageKey = VertxKafkaTypeConverter.tryConvertToSerializedType(exchange,
                configurationOptionsProxy.getMessageKey(exchange),
                configurationOptionsProxy.getKeySerializer(exchange));

        final Object messageValue = VertxKafkaTypeConverter.tryConvertToSerializedType(exchange, inputData,
                configurationOptionsProxy.getValueSerializer(exchange));
        final Integer partitionId = configurationOptionsProxy.getPartitionId(exchange);

        return KafkaProducerRecord.create(topic, messageKey, messageValue, partitionId);
    }
}
