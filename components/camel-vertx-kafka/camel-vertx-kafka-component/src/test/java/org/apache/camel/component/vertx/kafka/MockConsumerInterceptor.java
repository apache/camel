package org.apache.camel.component.vertx.kafka;

import java.util.ArrayList;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerInterceptor;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;

public class MockConsumerInterceptor implements ConsumerInterceptor<String, String> {

    public static ArrayList<ConsumerRecords<String, String>> recordsCaptured = new ArrayList<>();

    @Override
    public ConsumerRecords<String, String> onConsume(ConsumerRecords<String, String> consumerRecords) {
        recordsCaptured.add(consumerRecords);
        return consumerRecords;
    }

    @Override
    public void onCommit(Map<TopicPartition, OffsetAndMetadata> map) {
        // noop
    }

    @Override
    public void close() {
        // noop
    }

    @Override
    public void configure(Map<String, ?> map) {
        // noop
    }
}
