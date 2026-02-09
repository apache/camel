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

import java.util.Arrays;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import software.amazon.awssdk.services.s3vectors.S3VectorsClient;
import software.amazon.awssdk.services.s3vectors.model.PutVectorsRequest;
import software.amazon.awssdk.services.s3vectors.model.PutVectorsResponse;
import software.amazon.awssdk.services.s3vectors.model.QueryVectorsRequest;
import software.amazon.awssdk.services.s3vectors.model.QueryVectorsResponse;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AWS2S3VectorsProducerTest {

    private AWS2S3VectorsEndpoint endpoint;
    private S3VectorsClient s3VectorsClient;
    private AWS2S3VectorsProducer producer;

    @BeforeEach
    public void setup() {
        DefaultCamelContext context = new DefaultCamelContext();
        s3VectorsClient = Mockito.mock(S3VectorsClient.class);

        AWS2S3VectorsConfiguration configuration = new AWS2S3VectorsConfiguration();
        configuration.setVectorBucketName("test-bucket");
        configuration.setVectorIndexName("test-index");

        endpoint = new AWS2S3VectorsEndpoint("aws2-s3-vectors://test-bucket", null, configuration);
        endpoint.setS3VectorsClient(s3VectorsClient);
        producer = new AWS2S3VectorsProducer(endpoint);
    }

    @Test
    public void testPutVectors() throws Exception {
        // Mock response
        PutVectorsResponse mockResponse = PutVectorsResponse.builder().build();
        when(s3VectorsClient.putVectors(any(PutVectorsRequest.class))).thenReturn(mockResponse);

        // Create exchange
        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.getIn().setHeader(AWS2S3VectorsConstants.OPERATION, AWS2S3VectorsOperations.putVectors);
        exchange.getIn().setHeader(AWS2S3VectorsConstants.VECTOR_ID, "test-vec-1");

        List<Float> vectorData = Arrays.asList(0.1f, 0.2f, 0.3f);
        exchange.getIn().setBody(vectorData);

        // Process
        producer.process(exchange);

        // Verify
        verify(s3VectorsClient).putVectors(any(PutVectorsRequest.class));
        assertNotNull(exchange.getMessage().getBody());
    }

    @Test
    public void testQueryVectors() throws Exception {
        // Mock response
        QueryVectorsResponse mockResponse = QueryVectorsResponse.builder()
                .vectors(Arrays.asList())
                .build();
        when(s3VectorsClient.queryVectors(any(QueryVectorsRequest.class))).thenReturn(mockResponse);

        // Create exchange
        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.getIn().setHeader(AWS2S3VectorsConstants.OPERATION, AWS2S3VectorsOperations.queryVectors);
        exchange.getIn().setHeader(AWS2S3VectorsConstants.TOP_K, 5);

        List<Float> queryVector = Arrays.asList(0.1f, 0.2f, 0.3f);
        exchange.getIn().setBody(queryVector);

        // Process
        producer.process(exchange);

        // Verify
        verify(s3VectorsClient).queryVectors(any(QueryVectorsRequest.class));
        assertNotNull(exchange.getMessage().getBody());
    }
}
