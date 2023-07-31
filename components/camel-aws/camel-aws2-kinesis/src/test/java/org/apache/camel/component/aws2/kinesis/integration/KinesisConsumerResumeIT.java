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

package org.apache.camel.component.aws2.kinesis.integration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.camel.EndpointInject;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.kinesis.Kinesis2Constants;
import org.apache.camel.component.aws2.kinesis.consumer.KinesisResumeAdapter;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.resume.TransientResumeStrategy;
import org.apache.camel.test.infra.aws.common.AWSCommon;
import org.apache.camel.test.infra.aws.common.services.AWSService;
import org.apache.camel.test.infra.aws2.clients.AWSSDKClientUtils;
import org.apache.camel.test.infra.aws2.services.AWSServiceFactory;
import org.apache.camel.test.infra.common.TestUtils;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.camel.util.ObjectHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.shaded.org.awaitility.Awaitility;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.GetShardIteratorRequest;
import software.amazon.awssdk.services.kinesis.model.PutRecordsResponse;
import software.amazon.awssdk.services.kinesis.model.PutRecordsResultEntry;
import software.amazon.awssdk.services.kinesis.model.ShardIteratorType;

import static org.apache.camel.test.infra.aws2.clients.KinesisUtils.createStream;
import static org.apache.camel.test.infra.aws2.clients.KinesisUtils.deleteStream;
import static org.apache.camel.test.infra.aws2.clients.KinesisUtils.putRecords;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

/*
 * This test simulates resuming consumption from AWS Kinesis - consuming right at the middle of the batch of messages
 * sent.
 */
public class KinesisConsumerResumeIT extends CamelTestSupport {
    private static final class KinesisData {
        private String partition;
        private String body;

        @Override
        public String toString() {
            return "KinesisData{" +
                   "partition='" + partition + '\'' +
                   ", body='" + body + '\'' +
                   '}';
        }
    }

    private static final class TestKinesisResumeAdapter implements KinesisResumeAdapter {
        private List<PutRecordsResponse> previousRecords;
        private final int expectedCount;
        private GetShardIteratorRequest.Builder builder;

        private TestKinesisResumeAdapter(int expectedCount) {
            this.expectedCount = expectedCount;
        }

        @Override
        public void setRequestBuilder(GetShardIteratorRequest.Builder builder) {
            this.builder = ObjectHelper.notNull(builder, "builder");
        }

        @Override
        public void setStreamName(String streamName) {
            ObjectHelper.notNull(streamName, "streamName");
        }

        @Override
        public void resume() {
            LOG.debug("Waiting for data");
            Awaitility.await().atMost(1, TimeUnit.MINUTES).until(() -> !previousRecords.isEmpty());

            final PutRecordsResultEntry putRecordsResultEntry = previousRecords.get(0).records().get(expectedCount);

            LOG.info("Setting sequence number to: {}", putRecordsResultEntry.sequenceNumber());

            builder.startingSequenceNumber(putRecordsResultEntry.sequenceNumber());
            builder.shardIteratorType(ShardIteratorType.AT_SEQUENCE_NUMBER);
        }

        public void setPreviousRecords(List<PutRecordsResponse> previousRecords) {
            this.previousRecords = previousRecords;
        }
    }

    @RegisterExtension
    public static AWSService awsService = AWSServiceFactory.createKinesisService();

    private static final Logger LOG = LoggerFactory.getLogger(KinesisProducerIT.class);

    @EndpointInject("mock:result")
    private MockEndpoint result;

    private KinesisClient client;
    private String streamName = AWSCommon.KINESIS_STREAM_BASE_NAME + "-cons-" + TestUtils.randomWithRange(0, 100);
    private final int messageCount = 20;
    private final int expectedCount = messageCount / 2;
    private List<KinesisData> receivedMessages = new ArrayList<>();
    private List<PutRecordsResponse> previousRecords;
    private TestKinesisResumeAdapter adapter = new TestKinesisResumeAdapter(expectedCount);

    @Override
    protected RouteBuilder createRouteBuilder() {
        client = AWSSDKClientUtils.newKinesisClient();

        context.getRegistry().bind("amazonKinesisClient", client);

        return new RouteBuilder() {
            @Override
            public void configure() {
                bindToRegistry("testResumeStrategy", new TransientResumeStrategy(adapter));

                String kinesisEndpointUri = "aws2-kinesis://%s?amazonKinesisClient=#amazonKinesisClient";

                fromF(kinesisEndpointUri, streamName)
                        .process(exchange -> {
                            KinesisData data = new KinesisData();
                            final Message message = exchange.getMessage();

                            if (message != null) {
                                data.body = message.getBody(String.class);
                                data.partition = message.getHeader(Kinesis2Constants.PARTITION_KEY, String.class);
                            }

                            receivedMessages.add(data);
                        })
                        .resumable("testResumeStrategy")
                        .to("mock:result");
            }
        };
    }

    @BeforeEach
    public void prepareEnvironment() {
        createStream(client, streamName);

        previousRecords = putRecords(client, streamName, messageCount);
        for (PutRecordsResponse response : previousRecords) {
            final List<PutRecordsResultEntry> records = response.records();

            for (PutRecordsResultEntry entry : records) {
                LOG.debug("Sequence {}", entry.sequenceNumber());
            }
        }

        adapter.setPreviousRecords(previousRecords);
    }

    @AfterEach
    public void cleanUp() {
        deleteStream(client, streamName);
    }

    @DisplayName("Tests that the component can resume messages from AWS Kinesis")
    @Timeout(value = 3, unit = TimeUnit.MINUTES)
    @Test
    void testProduceMessages() {
        result.expectedMessageCount(expectedCount);

        await().atMost(2, TimeUnit.MINUTES)
                .untilAsserted(() -> result.assertIsSatisfied());

        assertEquals(expectedCount, receivedMessages.size());
        for (KinesisData data : receivedMessages) {
            ObjectHelper.notNull(data, "data");
            LOG.info("Received: {}", data.body);
            assertNotNull(data.body, "The body should not be null");
            assertNotNull(data.partition, "The partition should not be null");
            /*
             In this test scenario message "1" is sent to partition-1, message "2" is sent to partition-2,
             and so on. This is just testing that the code is not mixing things up.
             */
            assertTrue(data.partition.endsWith(data.body), "The data/partition mismatch for record: " + data);
        }
    }
}
