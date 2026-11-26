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
package org.apache.camel.component.aws2.kinesis.transform;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.cloudevents.CloudEvent;
import org.apache.camel.component.aws2.kinesis.Kinesis2Constants;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.DataType;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class KinesisCloudEventDataTypeTransformerTest {

    private final DefaultCamelContext camelContext = new DefaultCamelContext();

    private final KinesisCloudEventDataTypeTransformer transformer = new KinesisCloudEventDataTypeTransformer();

    @Test
    void theSourceIsTheStream() throws Exception {
        // CAMEL-27101: the source was built from the partition key that the sender sets on each record
        for (String partitionKey : List.of("pk1", "pk2")) {
            Exchange exchange = new DefaultExchange(camelContext);
            exchange.getMessage().setHeader(Kinesis2Constants.STREAM_NAME, "orders");
            exchange.getMessage().setHeader(Kinesis2Constants.PARTITION_KEY, partitionKey);
            exchange.getMessage().setHeader(Kinesis2Constants.SHARD_ID, "shardId-000000000000");
            transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

            assertEquals("aws.kinesis.stream.orders", exchange.getMessage().getHeader(CloudEvent.CAMEL_CLOUD_EVENT_SOURCE));
            assertEquals("shardId-000000000000", exchange.getMessage().getHeader(CloudEvent.CAMEL_CLOUD_EVENT_SUBJECT));
        }
    }

    @Test
    void theSourceIsNotThePartitionKey() throws Exception {
        // a message without the stream name of the consumer gets no source
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setHeader(Kinesis2Constants.PARTITION_KEY, "pk1");
        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

        assertNull(exchange.getMessage().getHeader(CloudEvent.CAMEL_CLOUD_EVENT_SOURCE));
    }

    @Test
    void theBodyTypesSayTheBody() throws Exception {
        // CAMEL-25928: any body, as transformerIn says, and the same body after, as transformerOut says
        BodyTypes bodyTypes = KinesisCloudEventDataTypeTransformer.class.getAnnotation(BodyTypes.class);
        assertEquals(Carrier.ANY, bodyTypes.transformerIn().value()[0]);
        assertEquals(Carrier.UNCHANGED, bodyTypes.transformerOut().value()[0]);
        for (Object body : Arrays.asList("Test", "Test".getBytes(StandardCharsets.UTF_8),
                new ByteArrayInputStream("Test".getBytes(StandardCharsets.UTF_8)), Map.of("key", "value"), 5, null)) {
            Exchange exchange = new DefaultExchange(camelContext);
            exchange.getMessage().setBody(body);
            transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);
            assertSame(body, exchange.getMessage().getBody(), String.valueOf(body));
            assertEquals("org.apache.camel.event.aws.kinesis.getRecords",
                    exchange.getMessage().getHeader(CloudEvent.CAMEL_CLOUD_EVENT_TYPE));
        }
    }
}
