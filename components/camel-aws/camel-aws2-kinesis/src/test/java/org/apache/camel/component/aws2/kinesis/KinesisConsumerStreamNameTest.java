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
package org.apache.camel.component.aws2.kinesis;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.apache.camel.Exchange;
import org.apache.camel.cloudevents.CloudEvent;
import org.apache.camel.component.aws2.kinesis.transform.KinesisCloudEventDataTypeTransformer;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.DataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.Record;
import software.amazon.awssdk.services.kinesis.model.Shard;
import software.amazon.kinesis.retrieval.KinesisClientRecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * Both consumers set the name of the stream on each record, and the CloudEvent transformer builds the source from it.
 */
class KinesisConsumerStreamNameTest {

    private final DefaultCamelContext context = new DefaultCamelContext();
    private Kinesis2Endpoint endpoint;

    @BeforeEach
    void setUp() {
        Kinesis2Component component = new Kinesis2Component(context);
        component.start();
        Kinesis2Configuration configuration = new Kinesis2Configuration();
        configuration.setStreamName("orders");
        configuration.setApplicationName("app");
        // a client, so the endpoint does not build a real AWS client (which needs a region) on start
        configuration.setAmazonKinesisClient(mock(KinesisClient.class));
        endpoint = new Kinesis2Endpoint("aws2-kinesis:orders", configuration, component);
        endpoint.start();
    }

    @AfterEach
    void tearDown() {
        endpoint.stop();
        context.stop();
    }

    @Test
    void theConsumerSetsTheStreamName() {
        Kinesis2Consumer consumer = new Kinesis2Consumer(endpoint, exchange -> {
        });
        Shard shard = Shard.builder().shardId("shardId-000000000001").build();
        for (String partitionKey : new String[] { "pk1", "pk2" }) {
            Record record = Record.builder().partitionKey(partitionKey).sequenceNumber("1")
                    .approximateArrivalTimestamp(Instant.now())
                    .data(SdkBytes.fromString("hello", StandardCharsets.UTF_8)).build();
            Exchange exchange = consumer.createExchange(shard, record);

            assertEquals("orders", exchange.getMessage().getHeader(Kinesis2Constants.STREAM_NAME));
            assertSource(exchange);
        }
    }

    @Test
    void theKclConsumerSetsTheStreamName() {
        KclKinesis2Consumer consumer = new KclKinesis2Consumer(endpoint, exchange -> {
        });
        KclKinesis2Consumer.CamelKinesisRecordProcessor processor = consumer.new CamelKinesisRecordProcessor(endpoint);
        for (String partitionKey : new String[] { "pk1", "pk2" }) {
            KinesisClientRecord record = KinesisClientRecord.builder().partitionKey(partitionKey).sequenceNumber("1")
                    .data(ByteBuffer.wrap("hello".getBytes(StandardCharsets.UTF_8))).build();
            Exchange exchange = processor.createExchange(record, "shardId-000000000001");

            assertEquals("orders", exchange.getMessage().getHeader(Kinesis2Constants.STREAM_NAME));
            assertSource(exchange);
        }
    }

    private static void assertSource(Exchange exchange) {
        // CAMEL-27101: the source was built from the partition key that the sender sets on each record
        new KinesisCloudEventDataTypeTransformer().transform(exchange.getMessage(), DataType.ANY, DataType.ANY);
        assertEquals("aws.kinesis.stream.orders", exchange.getMessage().getHeader(CloudEvent.CAMEL_CLOUD_EVENT_SOURCE));
    }
}
