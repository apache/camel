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
package org.apache.camel.component.aws2.s3vectors;

import java.util.List;

import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import software.amazon.awssdk.services.s3vectors.S3VectorsClient;
import software.amazon.awssdk.services.s3vectors.model.QueryVectorsRequest;
import software.amazon.awssdk.services.s3vectors.model.QueryVectorsResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class AWS2S3VectorsConsumerTest {

    @Test
    void consumerSendsPositiveTopK() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            S3VectorsClient client = Mockito.mock(S3VectorsClient.class);
            ArgumentCaptor<QueryVectorsRequest> captor = ArgumentCaptor.forClass(QueryVectorsRequest.class);
            when(client.queryVectors(captor.capture()))
                    .thenReturn(QueryVectorsResponse.builder().vectors(List.of()).build());

            AWS2S3VectorsEndpoint endpoint = context.getEndpoint(
                    "aws2-s3-vectors://test-bucket?vectorIndexName=test-index&accessKey=test&secretKey=test",
                    AWS2S3VectorsEndpoint.class);
            endpoint.getConfiguration().setConsumerQueryVector("0.1,0.2,0.3");
            endpoint.setS3VectorsClient(client);

            AWS2S3VectorsConsumer consumer = (AWS2S3VectorsConsumer) endpoint.createConsumer(exchange -> {
            });

            consumer.poll();

            // Before the fix the topK was Math.min(maxMessagesPerPoll, topK) where maxMessagesPerPoll was the
            // unwired base-class field (0), so topK(0) was sent and AWS returned nothing. It must now be >= 1.
            assertThat(captor.getValue().topK())
                    .as("topK sent to AWS S3 Vectors must be >= 1")
                    .isEqualTo(10);
        }
    }

    @Test
    void delayOptionDrivesTheConsumerPollInterval() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            AWS2S3VectorsEndpoint endpoint = context.getEndpoint(
                    "aws2-s3-vectors://test-bucket?vectorIndexName=test-index&delay=1234&accessKey=test&secretKey=test",
                    AWS2S3VectorsEndpoint.class);

            AWS2S3VectorsConsumer consumer = (AWS2S3VectorsConsumer) endpoint.createConsumer(exchange -> {
            });

            // delay used to be ignored (a shadow field on the configuration); it must now drive the actual poll interval
            assertThat(consumer.getDelay()).isEqualTo(1234L);
        }
    }
}
