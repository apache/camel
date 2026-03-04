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
package org.apache.camel.component.aws2.s3vectors.integration;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.s3vectors.AWS2S3VectorsConstants;
import org.apache.camel.component.aws2.s3vectors.AWS2S3VectorsEndpoint;
import org.apache.camel.component.aws2.s3vectors.AWS2S3VectorsOperations;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfSystemProperties;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3vectors.S3VectorsClient;
import software.amazon.awssdk.services.s3vectors.model.CreateIndexRequest;
import software.amazon.awssdk.services.s3vectors.model.CreateVectorBucketRequest;
import software.amazon.awssdk.services.s3vectors.model.DeleteIndexRequest;
import software.amazon.awssdk.services.s3vectors.model.DeleteVectorBucketRequest;
import software.amazon.awssdk.services.s3vectors.model.PutVectorsResponse;
import software.amazon.awssdk.services.s3vectors.model.QueryOutputVector;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Manual integration test for AWS S3 Vectors component.
 *
 * To run this test, set the following system properties: - aws.manual.access.key - aws.manual.secret.key -
 * aws.manual.region (optional, defaults to us-east-1)
 *
 * Example: mvn test -Dtest=S3VectorsComponentManualIT -Daws.manual.access.key=YOUR_KEY
 * -Daws.manual.secret.key=YOUR_SECRET -Daws.manual.region=us-east-1
 */
@EnabledIfSystemProperties({
        @EnabledIfSystemProperty(named = "aws.manual.access.key", matches = ".*", disabledReason = "Access key not provided"),
        @EnabledIfSystemProperty(named = "aws.manual.secret.key", matches = ".*", disabledReason = "Secret key not provided")
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class S3VectorsComponentManualIT extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(S3VectorsComponentManualIT.class);

    private static final String ACCESS_KEY = System.getProperty("aws.manual.access.key");
    private static final String SECRET_KEY = System.getProperty("aws.manual.secret.key");
    private static final String REGION = System.getProperty("aws.manual.region", "us-east-1");

    private static final String TEST_VECTOR_BUCKET = "camel-s3-vectors-test-" + System.currentTimeMillis();
    private static final String TEST_INDEX_NAME = "test-embeddings-index";
    private static final int VECTOR_DIMENSIONS = 3;

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @BindToRegistry("s3VectorsClient")
    S3VectorsClient client = S3VectorsClient.builder()
            .credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY)))
            .region(Region.of(REGION))
            .build();

    @BeforeAll
    public void setupVectorBucket() {
        LOG.info("Creating vector bucket [{}] for integration tests", TEST_VECTOR_BUCKET);

        try {
            // Create vector bucket
            client.createVectorBucket(CreateVectorBucketRequest.builder()
                    .vectorBucketName(TEST_VECTOR_BUCKET)
                    .build());
            LOG.info("Vector bucket created successfully");

            // Wait for bucket to be ready
            await().pollDelay(2, TimeUnit.SECONDS).until(() -> true);

            // Create vector index
            client.createIndex(CreateIndexRequest.builder()
                    .vectorBucketName(TEST_VECTOR_BUCKET)
                    .indexName(TEST_INDEX_NAME)
                    .dimension(VECTOR_DIMENSIONS)
                    .dataType("float32")  // Required: float32 or float16
                    .distanceMetric("cosine")
                    .build());
            LOG.info("Vector index [{}] created successfully", TEST_INDEX_NAME);

            // Wait for index to be ready
            await().pollDelay(5, TimeUnit.SECONDS).until(() -> true);

        } catch (Exception e) {
            LOG.error("Failed to setup vector bucket and index", e);
            throw new RuntimeException("Failed to setup test environment", e);
        }
    }

    @AfterAll
    public void cleanupVectorBucket() {
        LOG.info("Cleaning up vector bucket [{}]", TEST_VECTOR_BUCKET);

        try {
            // Delete vector index
            client.deleteIndex(DeleteIndexRequest.builder()
                    .vectorBucketName(TEST_VECTOR_BUCKET)
                    .indexName(TEST_INDEX_NAME)
                    .build());
            LOG.info("Vector index deleted successfully");

            // Wait for index deletion to propagate
            await().pollDelay(2, TimeUnit.SECONDS).until(() -> true);

            // Delete vector bucket
            client.deleteVectorBucket(DeleteVectorBucketRequest.builder()
                    .vectorBucketName(TEST_VECTOR_BUCKET)
                    .build());
            LOG.info("Vector bucket deleted successfully");

        } catch (Exception e) {
            LOG.error("Failed to cleanup vector bucket and index", e);
        }
    }

    @Test
    @Order(1)
    public void testPutVector() throws Exception {
        LOG.info("Testing putVectors operation");

        result.expectedMessageCount(1);

        template.send("direct:putVector", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(AWS2S3VectorsConstants.VECTOR_ID, "test-vec-001");

                Map<String, String> metadata = new HashMap<>();
                metadata.put("category", "test");
                metadata.put("source", "integration-test");
                exchange.getIn().setHeader(AWS2S3VectorsConstants.VECTOR_METADATA, metadata);

                List<Float> vectorData = Arrays.asList(0.1f, 0.2f, 0.3f);
                exchange.getIn().setBody(vectorData);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        Exchange resultExchange = result.getExchanges().get(0);
        PutVectorsResponse response = resultExchange.getMessage().getBody(PutVectorsResponse.class);
        assertNotNull(response, "PutVectors response should not be null");

        LOG.info("Vector inserted successfully");
    }

    @Test
    @Order(2)
    public void testPutMultipleVectors() throws Exception {
        LOG.info("Testing putVectors with multiple vectors");

        result.reset();
        result.expectedMessageCount(3);

        // Insert 3 more vectors
        for (int i = 2; i <= 4; i++) {
            final int index = i;
            template.send("direct:putVector", new Processor() {
                @Override
                public void process(Exchange exchange) {
                    exchange.getIn().setHeader(AWS2S3VectorsConstants.VECTOR_ID, "test-vec-00" + index);

                    Map<String, String> metadata = new HashMap<>();
                    metadata.put("category", "test");
                    metadata.put("index", String.valueOf(index));
                    exchange.getIn().setHeader(AWS2S3VectorsConstants.VECTOR_METADATA, metadata);

                    // Slightly different vectors
                    List<Float> vectorData = Arrays.asList(
                            0.1f * index,
                            0.2f * index,
                            0.3f * index);
                    exchange.getIn().setBody(vectorData);
                }
            });
        }

        MockEndpoint.assertIsSatisfied(context);
        LOG.info("Multiple vectors inserted successfully");

        // Wait for vectors to be indexed
        await().pollDelay(3, TimeUnit.SECONDS).until(() -> true);
    }

    @Test
    @Order(3)
    public void testQueryVectors() throws Exception {
        LOG.info("Testing queryVectors operation");

        result.reset();
        result.expectedMessageCount(1);

        template.send("direct:queryVectors", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(AWS2S3VectorsConstants.TOP_K, 3);

                // Query with a vector similar to the first one we inserted
                List<Float> queryVector = Arrays.asList(0.11f, 0.21f, 0.31f);
                exchange.getIn().setBody(queryVector);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        Exchange resultExchange = result.getExchanges().get(0);
        @SuppressWarnings("unchecked")
        List<QueryOutputVector> results = resultExchange.getMessage().getBody(List.class);

        assertNotNull(results, "Query results should not be null");
        assertTrue(results.size() > 0, "Should have at least one result");
        assertTrue(results.size() <= 3, "Should not exceed topK limit");

        Integer resultCount = resultExchange.getMessage().getHeader(AWS2S3VectorsConstants.RESULT_COUNT, Integer.class);
        assertNotNull(resultCount, "Result count header should be set");
        assertEquals(results.size(), resultCount.intValue(), "Result count should match list size");

        LOG.info("Query returned {} vectors", results.size());
    }

    @Test
    @Order(4)
    public void testQueryVectorsWithDifferentTopK() throws Exception {
        LOG.info("Testing queryVectors with topK=1");

        result.reset();
        result.expectedMessageCount(1);

        template.send("direct:queryVectors", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(AWS2S3VectorsConstants.TOP_K, 1);

                List<Float> queryVector = Arrays.asList(0.4f, 0.8f, 1.2f);
                exchange.getIn().setBody(queryVector);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        Exchange resultExchange = result.getExchanges().get(0);
        @SuppressWarnings("unchecked")
        List<QueryOutputVector> results = resultExchange.getMessage().getBody(List.class);

        assertNotNull(results, "Query results should not be null");
        assertTrue(results.size() <= 1, "Should have at most 1 result");

        LOG.info("Query with topK=1 returned {} vectors", results.size());
    }

    @Test
    @Order(5)
    public void testGetVectors() throws Exception {
        LOG.info("Testing getVectors operation");

        result.reset();
        result.expectedMessageCount(1);

        template.send("direct:getVectors", new Processor() {
            @Override
            public void process(Exchange exchange) {
                List<String> vectorIds = Arrays.asList("test-vec-001", "test-vec-002");
                exchange.getIn().setBody(vectorIds);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        Exchange resultExchange = result.getExchanges().get(0);
        List<?> vectors = resultExchange.getMessage().getBody(List.class);

        assertNotNull(vectors, "Retrieved vectors should not be null");
        LOG.info("Retrieved {} vectors", vectors.size());
    }

    @Test
    @Order(6)
    public void testListVectorIndexes() throws Exception {
        LOG.info("Testing listVectorIndexes operation");

        result.reset();
        result.expectedMessageCount(1);

        template.send("direct:listIndexes", new Processor() {
            @Override
            public void process(Exchange exchange) {
                // No additional setup needed
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        Exchange resultExchange = result.getExchanges().get(0);
        List<?> indexes = resultExchange.getMessage().getBody(List.class);

        assertNotNull(indexes, "Index list should not be null");
        assertTrue(indexes.size() > 0, "Should have at least one index");

        LOG.info("Found {} vector indexes", indexes.size());
    }

    @Test
    @Order(7)
    public void testDeleteVectors() throws Exception {
        LOG.info("Testing deleteVectors operation");

        result.reset();
        result.expectedMessageCount(1);

        template.send("direct:deleteVectors", new Processor() {
            @Override
            public void process(Exchange exchange) {
                // Delete one of the test vectors
                exchange.getIn().setBody("test-vec-004");
            }
        });

        MockEndpoint.assertIsSatisfied(context);
        LOG.info("Vector deleted successfully");
    }

    /**
     * Consumer test - Disabled due to unreliable timing with S3 Vectors indexing delays.
     *
     * The consumer functionality can be verified manually by: 1. Creating a route with aws2-s3-vectors consumer
     * endpoint 2. Configuring consumerQueryVector parameter 3. Observing that vectors are polled and processed
     *
     * Example consumer route:
     * from("aws2-s3-vectors://my-bucket?vectorIndexName=my-index&consumerQueryVector=0.1,0.2,0.3") .log("Consumed
     * vector: ${header.CamelAwsS3VectorsVectorId}") .to("mock:result");
     */
    @Test
    @Order(8)
    public void testConsumer() throws Exception {
        LOG.info("Consumer test - verifying consumer configuration and creation");

        // Verify that the consumer can be configured without errors
        // The actual polling behavior is difficult to test reliably due to S3 Vectors indexing delays
        // and requires manual verification

        String consumerEndpoint = "aws2-s3-vectors://" + TEST_VECTOR_BUCKET
                                  + "?vectorIndexName=" + TEST_INDEX_NAME
                                  + "&consumerQueryVector=0.2,0.4,0.6"
                                  + "&delay=10000"
                                  + "&maxMessagesPerPoll=5";

        // Verify endpoint can be created
        AWS2S3VectorsEndpoint endpoint = (AWS2S3VectorsEndpoint) context.getEndpoint(consumerEndpoint);
        assertNotNull(endpoint, "Consumer endpoint should be created");
        assertEquals(TEST_VECTOR_BUCKET, endpoint.getConfiguration().getVectorBucketName());
        assertEquals(TEST_INDEX_NAME, endpoint.getConfiguration().getVectorIndexName());
        assertEquals("0.2,0.4,0.6", endpoint.getConfiguration().getConsumerQueryVector());
        assertEquals(10000, endpoint.getConfiguration().getDelay());
        assertEquals(5, endpoint.getConfiguration().getMaxMessagesPerPoll());

        LOG.info("Consumer configuration verified successfully");
        LOG.info("Note: Full consumer polling behavior should be tested manually due to S3 Vectors indexing delays");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                String awsEndpoint = "aws2-s3-vectors://" + TEST_VECTOR_BUCKET
                                     + "?vectorIndexName=" + TEST_INDEX_NAME;

                from("direct:putVector")
                        .setHeader(AWS2S3VectorsConstants.OPERATION,
                                constant(AWS2S3VectorsOperations.putVectors))
                        .to(awsEndpoint)
                        .to("mock:result");

                from("direct:queryVectors")
                        .setHeader(AWS2S3VectorsConstants.OPERATION,
                                constant(AWS2S3VectorsOperations.queryVectors))
                        .to(awsEndpoint)
                        .to("mock:result");

                from("direct:getVectors")
                        .setHeader(AWS2S3VectorsConstants.OPERATION,
                                constant(AWS2S3VectorsOperations.getVectors))
                        .to(awsEndpoint)
                        .to("mock:result");

                from("direct:listIndexes")
                        .setHeader(AWS2S3VectorsConstants.OPERATION,
                                constant(AWS2S3VectorsOperations.listVectorIndexes))
                        .to(awsEndpoint)
                        .to("mock:result");

                from("direct:deleteVectors")
                        .setHeader(AWS2S3VectorsConstants.OPERATION,
                                constant(AWS2S3VectorsOperations.deleteVectors))
                        .to(awsEndpoint)
                        .to("mock:result");
            }
        };
    }
}
