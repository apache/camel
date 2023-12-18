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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.kinesis.consumer.KinesisConsumerOffsetProcessor;
import org.apache.camel.component.aws2.kinesis.consumer.KinesisResumeStrategyConfiguration;
import org.apache.camel.processor.resume.TransientResumeStrategy;
import org.apache.camel.resume.cache.ResumeCache;
import org.apache.camel.test.infra.aws.common.services.AWSService;
import org.apache.camel.test.infra.aws2.clients.AWSSDKClientUtils;
import org.apache.camel.test.infra.aws2.services.AWSServiceFactory;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.shaded.org.awaitility.Awaitility;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.PutRecordRequest;

import static org.apache.camel.test.infra.aws2.clients.KinesisUtils.createStream;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class KinesisConsumerResumeAfterRestartIT extends CamelTestSupport {

    @RegisterExtension
    public static AWSService awsService = AWSServiceFactory.createKinesisService();
    private KinesisClient client;

    String streamName = "my-stream";

    List<String> receivedMessages = new ArrayList<>();

    ResumeCache resumeCache = TransientResumeStrategy.createSimpleCache();

    @Override
    protected RouteBuilder createRouteBuilder() {
        client = AWSSDKClientUtils.newKinesisClient();

        context.getRegistry().bind("amazonKinesisClient", AWSSDKClientUtils.newKinesisClient());

        return new RouteBuilder() {
            @Override
            public void configure() {
                String kinesisEndpointUri = "aws2-kinesis://%s?amazonKinesisClient=#amazonKinesisClient";

                fromF(kinesisEndpointUri, streamName)
                        // commenting out the strategy will cause the test to fail as event "First" will be consumed twice
                        .resumable().configuration(KinesisResumeStrategyConfiguration.builder().withResumeCache(resumeCache))
                        .process(new KinesisConsumerOffsetProcessor())
                        .process(exchange -> receivedMessages.add(exchange.getMessage().getBody(String.class)));
            }
        };
    }

    @BeforeEach
    public void prepareEnvironment() {
        createStream(client, streamName);
    }

    private void sendEvent(String payload) {
        client.putRecord(PutRecordRequest.builder().streamName(streamName).partitionKey("my-key")
                .data(SdkBytes.fromUtf8String(payload)).build());
    }

    @Test
    void shouldResumeConsumptionAfterRestart() {

        sendEvent("First");
        Awaitility.await().until(() -> receivedMessages.contains("First"));

        restartContext();

        sendEvent("Second");
        Awaitility.await().until(() -> receivedMessages.contains("Second"));

        assertEquals(2, receivedMessages.size());
    }

    private void restartContext() {
        context.stop();

        // stop also seems to close the kinesis client, therefor we need to provide a new one
        context.getRegistry().bind("amazonKinesisClient", AWSSDKClientUtils.newKinesisClient());

        context.start();
    }
}
