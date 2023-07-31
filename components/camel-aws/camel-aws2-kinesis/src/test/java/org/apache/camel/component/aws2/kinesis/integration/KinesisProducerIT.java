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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.camel.EndpointInject;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.kinesis.Kinesis2Constants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.aws.common.AWSCommon;
import org.apache.camel.test.infra.aws.common.services.AWSService;
import org.apache.camel.test.infra.aws2.clients.AWSSDKClientUtils;
import org.apache.camel.test.infra.aws2.clients.KinesisUtils;
import org.apache.camel.test.infra.aws2.services.AWSServiceFactory;
import org.apache.camel.test.infra.common.TestUtils;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.shaded.org.awaitility.Awaitility;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.GetRecordsRequest;
import software.amazon.awssdk.services.kinesis.model.GetRecordsResponse;
import software.amazon.awssdk.services.kinesis.model.Record;

import static org.apache.camel.test.infra.aws2.clients.KinesisUtils.createStream;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class KinesisProducerIT extends CamelTestSupport {
    @RegisterExtension
    public static AWSService awsService = AWSServiceFactory.createKinesisService();

    private static final Logger LOG = LoggerFactory.getLogger(KinesisProducerIT.class);

    private KinesisClient client;

    @EndpointInject("direct:start")
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    private String streamName = AWSCommon.KINESIS_STREAM_BASE_NAME + "-" + TestUtils.randomWithRange(0, 100);
    private List<Record> recordList;

    @BeforeEach
    public void prepareEnvironment() {
        createStream(client, streamName);
    }

    protected int consumeMessages() {
        try {
            GetRecordsRequest getRecordsRequest = KinesisUtils.getGetRecordsRequest(client, streamName);
            GetRecordsResponse response = client.getRecords(getRecordsRequest);

            recordList = response.records();
            for (Record record : recordList) {
                LOG.info("Received record: {}", record.data());
            }

            return recordList.size();
        } catch (Exception e) {
            LOG.error("Error consuming records: {}", e.getMessage(), e);
        }

        return 0;
    }

    @DisplayName("Tests that can produce data to a Kinesis instance")
    @Test
    public void send() {
        result.expectedMessageCount(2);

        template.send("direct:start", ExchangePattern.InOnly, exchange -> {
            exchange.getIn().setHeader(Kinesis2Constants.PARTITION_KEY, "partition-1");
            exchange.getIn().setBody("Kinesis Event 1.");
        });

        template.send("direct:start", ExchangePattern.InOut, exchange -> {
            exchange.getIn().setHeader(Kinesis2Constants.PARTITION_KEY, "partition-1");
            exchange.getIn().setBody("Kinesis Event 2.");
        });

        List<Record> records;
        Awaitility.await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(2, consumeMessages()));

        assertEquals("Kinesis Event 1.", recordList.get(0).data().asString(StandardCharsets.UTF_8));
        assertEquals("partition-1", recordList.get(0).partitionKey());
        assertEquals("Kinesis Event 2.", recordList.get(1).data().asString(StandardCharsets.UTF_8));
        assertEquals("partition-1", recordList.get(1).partitionKey());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        client = AWSSDKClientUtils.newKinesisClient();

        context.getRegistry().bind("amazonKinesisClient", client);

        return new RouteBuilder() {
            @Override
            public void configure() {
                String kinesisEndpointUri = "aws2-kinesis://%s?amazonKinesisClient=#amazonKinesisClient";

                from("direct:start").toF(kinesisEndpointUri, streamName);
            }
        };
    }
}
