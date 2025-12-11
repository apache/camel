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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.s3vectors.S3VectorsClient;
import software.amazon.awssdk.services.s3vectors.model.CreateIndexRequest;
import software.amazon.awssdk.services.s3vectors.model.CreateIndexResponse;
import software.amazon.awssdk.services.s3vectors.model.CreateVectorBucketRequest;
import software.amazon.awssdk.services.s3vectors.model.CreateVectorBucketResponse;
import software.amazon.awssdk.services.s3vectors.model.DeleteIndexRequest;
import software.amazon.awssdk.services.s3vectors.model.DeleteIndexResponse;
import software.amazon.awssdk.services.s3vectors.model.DeleteVectorBucketRequest;
import software.amazon.awssdk.services.s3vectors.model.DeleteVectorBucketResponse;
import software.amazon.awssdk.services.s3vectors.model.DeleteVectorsRequest;
import software.amazon.awssdk.services.s3vectors.model.DeleteVectorsResponse;
import software.amazon.awssdk.services.s3vectors.model.GetIndexRequest;
import software.amazon.awssdk.services.s3vectors.model.GetIndexResponse;
import software.amazon.awssdk.services.s3vectors.model.GetVectorBucketRequest;
import software.amazon.awssdk.services.s3vectors.model.GetVectorBucketResponse;
import software.amazon.awssdk.services.s3vectors.model.GetVectorsRequest;
import software.amazon.awssdk.services.s3vectors.model.GetVectorsResponse;
import software.amazon.awssdk.services.s3vectors.model.ListIndexesRequest;
import software.amazon.awssdk.services.s3vectors.model.ListIndexesResponse;
import software.amazon.awssdk.services.s3vectors.model.ListVectorBucketsRequest;
import software.amazon.awssdk.services.s3vectors.model.ListVectorBucketsResponse;
import software.amazon.awssdk.services.s3vectors.model.PutInputVector;
import software.amazon.awssdk.services.s3vectors.model.PutVectorsRequest;
import software.amazon.awssdk.services.s3vectors.model.PutVectorsResponse;
import software.amazon.awssdk.services.s3vectors.model.QueryVectorsRequest;
import software.amazon.awssdk.services.s3vectors.model.QueryVectorsResponse;
import software.amazon.awssdk.services.s3vectors.model.VectorData;

/**
 * A Producer which sends messages to AWS S3 Vectors for vector storage and similarity search
 */
public class AWS2S3VectorsProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(AWS2S3VectorsProducer.class);

    public AWS2S3VectorsProducer(final Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(final Exchange exchange) throws Exception {
        AWS2S3VectorsOperations operation = determineOperation(exchange);
        if (ObjectHelper.isEmpty(operation)) {
            throw new IllegalArgumentException("Operation must be specified");
        }

        switch (operation) {
            case putVectors:
                putVectors(getEndpoint().getS3VectorsClient(), exchange);
                break;
            case queryVectors:
                queryVectors(getEndpoint().getS3VectorsClient(), exchange);
                break;
            case deleteVectors:
                deleteVectors(getEndpoint().getS3VectorsClient(), exchange);
                break;
            case getVectors:
                getVectors(getEndpoint().getS3VectorsClient(), exchange);
                break;
            case createVectorBucket:
                createVectorBucket(getEndpoint().getS3VectorsClient(), exchange);
                break;
            case deleteVectorBucket:
                deleteVectorBucket(getEndpoint().getS3VectorsClient(), exchange);
                break;
            case listVectorBuckets:
                listVectorBuckets(getEndpoint().getS3VectorsClient(), exchange);
                break;
            case describeVectorBucket:
                describeVectorBucket(getEndpoint().getS3VectorsClient(), exchange);
                break;
            case createVectorIndex:
                createVectorIndex(getEndpoint().getS3VectorsClient(), exchange);
                break;
            case deleteVectorIndex:
                deleteVectorIndex(getEndpoint().getS3VectorsClient(), exchange);
                break;
            case listVectorIndexes:
                listVectorIndexes(getEndpoint().getS3VectorsClient(), exchange);
                break;
            case describeVectorIndex:
                describeVectorIndex(getEndpoint().getS3VectorsClient(), exchange);
                break;
            default:
                throw new IllegalArgumentException("Unsupported operation: " + operation);
        }
    }

    private AWS2S3VectorsOperations determineOperation(Exchange exchange) {
        AWS2S3VectorsOperations operation = exchange.getIn().getHeader(AWS2S3VectorsConstants.OPERATION,
                AWS2S3VectorsOperations.class);
        if (operation == null) {
            operation = getConfiguration().getOperation();
        }
        return operation;
    }

    private void putVectors(S3VectorsClient client, Exchange exchange) {
        Message message = exchange.getIn();
        String vectorBucketName = determineVectorBucketName(exchange);
        String vectorIndexName = determineVectorIndexName(exchange);

        // Get vector data from header or body
        Object vectorDataObj = message.getHeader(AWS2S3VectorsConstants.VECTOR_DATA);
        if (vectorDataObj == null) {
            vectorDataObj = message.getBody();
        }

        // Get vector ID
        String vectorId = message.getHeader(AWS2S3VectorsConstants.VECTOR_ID, String.class);
        if (vectorId == null) {
            vectorId = exchange.getExchangeId(); // Use exchange ID as default
        }

        // Get metadata if provided
        @SuppressWarnings("unchecked")
        Map<String, String> metadata = message.getHeader(AWS2S3VectorsConstants.VECTOR_METADATA, Map.class);

        // Convert vector data to List<Float>
        List<Float> vectorData = convertToFloatList(vectorDataObj);

        // Build the input vector
        PutInputVector.Builder vectorBuilder = PutInputVector.builder()
                .key(vectorId)
                .data(VectorData.builder().float32(vectorData).build());

        if (metadata != null && !metadata.isEmpty()) {
            // Convert Map to Document
            Document metadataDoc = convertMapToDocument(metadata);
            vectorBuilder.metadata(metadataDoc);
        }

        // Build and execute request
        PutVectorsRequest request = PutVectorsRequest.builder()
                .vectorBucketName(vectorBucketName)
                .indexName(vectorIndexName)
                .vectors(vectorBuilder.build())
                .build();

        LOG.trace("Putting vector [{}] to bucket [{}] index [{}]", vectorId, vectorBucketName, vectorIndexName);
        PutVectorsResponse response = client.putVectors(request);
        LOG.trace("Put vector response: {}", response);

        // Set response headers
        message.setHeader(AWS2S3VectorsConstants.VECTOR_BUCKET_NAME, vectorBucketName);
        message.setHeader(AWS2S3VectorsConstants.VECTOR_INDEX_NAME, vectorIndexName);
        message.setHeader(AWS2S3VectorsConstants.VECTOR_ID, vectorId);
        message.setBody(response);
    }

    private void queryVectors(S3VectorsClient client, Exchange exchange) {
        Message message = exchange.getIn();
        String vectorBucketName = determineVectorBucketName(exchange);
        String vectorIndexName = determineVectorIndexName(exchange);

        // Get query vector from header or body
        Object queryVectorObj = message.getHeader(AWS2S3VectorsConstants.QUERY_VECTOR);
        if (queryVectorObj == null) {
            queryVectorObj = message.getBody();
        }

        List<Float> queryVector = convertToFloatList(queryVectorObj);

        // Get query parameters
        Integer topK = message.getHeader(AWS2S3VectorsConstants.TOP_K, Integer.class);
        if (topK == null) {
            topK = getConfiguration().getTopK();
        }

        // Build and execute request (metadata filter can be complex, skip for now)
        QueryVectorsRequest.Builder requestBuilder = QueryVectorsRequest.builder()
                .vectorBucketName(vectorBucketName)
                .indexName(vectorIndexName)
                .queryVector(VectorData.builder().float32(queryVector).build())
                .topK(topK);

        LOG.trace("Querying vectors in bucket [{}] index [{}] with topK [{}]", vectorBucketName, vectorIndexName, topK);
        QueryVectorsResponse response = client.queryVectors(requestBuilder.build());
        LOG.trace("Query vectors response: {}", response);

        // Set response headers
        message.setHeader(AWS2S3VectorsConstants.RESULT_COUNT, response.vectors().size());
        message.setBody(response.vectors());
    }

    private void deleteVectors(S3VectorsClient client, Exchange exchange) {
        Message message = exchange.getIn();
        String vectorBucketName = determineVectorBucketName(exchange);
        String vectorIndexName = determineVectorIndexName(exchange);

        // Get vector IDs from header or body
        Object vectorIdsObj = message.getHeader(AWS2S3VectorsConstants.VECTOR_ID);
        if (vectorIdsObj == null) {
            vectorIdsObj = message.getBody();
        }

        List<String> vectorIds = convertToStringList(vectorIdsObj);

        // Build and execute request
        DeleteVectorsRequest request = DeleteVectorsRequest.builder()
                .vectorBucketName(vectorBucketName)
                .indexName(vectorIndexName)
                .keys(vectorIds)
                .build();

        LOG.trace("Deleting vectors [{}] from bucket [{}] index [{}]", vectorIds, vectorBucketName, vectorIndexName);
        DeleteVectorsResponse response = client.deleteVectors(request);
        LOG.trace("Delete vectors response: {}", response);

        message.setBody(response);
    }

    private void getVectors(S3VectorsClient client, Exchange exchange) {
        Message message = exchange.getIn();
        String vectorBucketName = determineVectorBucketName(exchange);
        String vectorIndexName = determineVectorIndexName(exchange);

        // Get vector IDs from header or body
        Object vectorIdsObj = message.getHeader(AWS2S3VectorsConstants.VECTOR_ID);
        if (vectorIdsObj == null) {
            vectorIdsObj = message.getBody();
        }

        List<String> vectorIds = convertToStringList(vectorIdsObj);

        // Build and execute request
        GetVectorsRequest request = GetVectorsRequest.builder()
                .vectorBucketName(vectorBucketName)
                .indexName(vectorIndexName)
                .keys(vectorIds)
                .build();

        LOG.trace("Getting vectors [{}] from bucket [{}] index [{}]", vectorIds, vectorBucketName, vectorIndexName);
        GetVectorsResponse response = client.getVectors(request);
        LOG.trace("Get vectors response: {}", response);

        message.setBody(response.vectors());
    }

    private void createVectorBucket(S3VectorsClient client, Exchange exchange) {
        Message message = exchange.getIn();
        String vectorBucketName = determineVectorBucketName(exchange);

        CreateVectorBucketRequest request = CreateVectorBucketRequest.builder()
                .vectorBucketName(vectorBucketName)
                .build();

        LOG.trace("Creating vector bucket [{}]", vectorBucketName);
        CreateVectorBucketResponse response = client.createVectorBucket(request);
        LOG.trace("Create vector bucket response: {}", response);

        message.setHeader(AWS2S3VectorsConstants.VECTOR_BUCKET_NAME, vectorBucketName);
        message.setHeader(AWS2S3VectorsConstants.VECTOR_BUCKET_ARN, response.vectorBucketArn());
        message.setBody(response);
    }

    private void deleteVectorBucket(S3VectorsClient client, Exchange exchange) {
        Message message = exchange.getIn();
        String vectorBucketName = determineVectorBucketName(exchange);

        DeleteVectorBucketRequest request = DeleteVectorBucketRequest.builder()
                .vectorBucketName(vectorBucketName)
                .build();

        LOG.trace("Deleting vector bucket [{}]", vectorBucketName);
        DeleteVectorBucketResponse response = client.deleteVectorBucket(request);
        LOG.trace("Delete vector bucket response: {}", response);

        message.setBody(response);
    }

    private void listVectorBuckets(S3VectorsClient client, Exchange exchange) {
        Message message = exchange.getIn();

        ListVectorBucketsRequest request = ListVectorBucketsRequest.builder().build();

        LOG.trace("Listing vector buckets");
        ListVectorBucketsResponse response = client.listVectorBuckets(request);
        LOG.trace("List vector buckets response: {}", response);

        message.setBody(response.vectorBuckets());
    }

    private void describeVectorBucket(S3VectorsClient client, Exchange exchange) {
        Message message = exchange.getIn();
        String vectorBucketName = determineVectorBucketName(exchange);

        GetVectorBucketRequest request = GetVectorBucketRequest.builder()
                .vectorBucketName(vectorBucketName)
                .build();

        LOG.trace("Describing vector bucket [{}]", vectorBucketName);
        GetVectorBucketResponse response = client.getVectorBucket(request);
        LOG.trace("Describe vector bucket response: {}", response);

        // Vector bucket ARN may not be available in response, skip setting it
        message.setBody(response);
    }

    private void createVectorIndex(S3VectorsClient client, Exchange exchange) {
        Message message = exchange.getIn();
        String vectorBucketName = determineVectorBucketName(exchange);
        String vectorIndexName = determineVectorIndexName(exchange);

        Integer dimensions = message.getHeader(AWS2S3VectorsConstants.VECTOR_DIMENSIONS, Integer.class);
        if (dimensions == null) {
            dimensions = getConfiguration().getVectorDimensions();
        }

        String dataType = message.getHeader(AWS2S3VectorsConstants.DATA_TYPE, String.class);
        if (dataType == null) {
            dataType = getConfiguration().getDataType();
        }

        String distanceMetric = message.getHeader(AWS2S3VectorsConstants.DISTANCE_METRIC, String.class);
        if (distanceMetric == null) {
            distanceMetric = getConfiguration().getDistanceMetric();
        }

        CreateIndexRequest request = CreateIndexRequest.builder()
                .vectorBucketName(vectorBucketName)
                .indexName(vectorIndexName)
                .dimension(dimensions)
                .dataType(dataType)
                .distanceMetric(distanceMetric)
                .build();

        LOG.trace("Creating vector index [{}] in bucket [{}] with dimensions [{}]", vectorIndexName, vectorBucketName,
                dimensions);
        CreateIndexResponse response = client.createIndex(request);
        LOG.trace("Create vector index response: {}", response);

        message.setHeader(AWS2S3VectorsConstants.VECTOR_INDEX_NAME, vectorIndexName);
        // Status may not be available in response
        message.setBody(response);
    }

    private void deleteVectorIndex(S3VectorsClient client, Exchange exchange) {
        Message message = exchange.getIn();
        String vectorBucketName = determineVectorBucketName(exchange);
        String vectorIndexName = determineVectorIndexName(exchange);

        DeleteIndexRequest request = DeleteIndexRequest.builder()
                .vectorBucketName(vectorBucketName)
                .indexName(vectorIndexName)
                .build();

        LOG.trace("Deleting vector index [{}] from bucket [{}]", vectorIndexName, vectorBucketName);
        DeleteIndexResponse response = client.deleteIndex(request);
        LOG.trace("Delete vector index response: {}", response);

        message.setBody(response);
    }

    private void listVectorIndexes(S3VectorsClient client, Exchange exchange) {
        Message message = exchange.getIn();
        String vectorBucketName = determineVectorBucketName(exchange);

        ListIndexesRequest request = ListIndexesRequest.builder()
                .vectorBucketName(vectorBucketName)
                .build();

        LOG.trace("Listing vector indexes in bucket [{}]", vectorBucketName);
        ListIndexesResponse response = client.listIndexes(request);
        LOG.trace("List vector indexes response: {}", response);

        message.setBody(response.indexes());
    }

    private void describeVectorIndex(S3VectorsClient client, Exchange exchange) {
        Message message = exchange.getIn();
        String vectorBucketName = determineVectorBucketName(exchange);
        String vectorIndexName = determineVectorIndexName(exchange);

        GetIndexRequest request = GetIndexRequest.builder()
                .vectorBucketName(vectorBucketName)
                .indexName(vectorIndexName)
                .build();

        LOG.trace("Describing vector index [{}] in bucket [{}]", vectorIndexName, vectorBucketName);
        GetIndexResponse response = client.getIndex(request);
        LOG.trace("Describe vector index response: {}", response);

        // Status may not be available in response
        message.setBody(response);
    }

    private String determineVectorBucketName(Exchange exchange) {
        String vectorBucketName = exchange.getIn().getHeader(AWS2S3VectorsConstants.VECTOR_BUCKET_NAME, String.class);
        if (vectorBucketName == null) {
            vectorBucketName = getConfiguration().getVectorBucketName();
        }
        if (vectorBucketName == null) {
            throw new IllegalArgumentException("Vector bucket name must be specified");
        }
        return vectorBucketName;
    }

    private String determineVectorIndexName(Exchange exchange) {
        String vectorIndexName = exchange.getIn().getHeader(AWS2S3VectorsConstants.VECTOR_INDEX_NAME, String.class);
        if (vectorIndexName == null) {
            vectorIndexName = getConfiguration().getVectorIndexName();
        }
        if (vectorIndexName == null) {
            throw new IllegalArgumentException("Vector index name must be specified");
        }
        return vectorIndexName;
    }

    @SuppressWarnings("unchecked")
    private List<Float> convertToFloatList(Object obj) {
        if (obj == null) {
            throw new IllegalArgumentException("Vector data cannot be null");
        }

        if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            List<Float> result = new ArrayList<>(list.size());
            for (Object item : list) {
                if (item instanceof Float) {
                    result.add((Float) item);
                } else if (item instanceof Number) {
                    result.add(((Number) item).floatValue());
                } else {
                    throw new IllegalArgumentException("Invalid vector data type: " + item.getClass());
                }
            }
            return result;
        } else if (obj instanceof float[]) {
            float[] array = (float[]) obj;
            List<Float> result = new ArrayList<>(array.length);
            for (float f : array) {
                result.add(f);
            }
            return result;
        } else if (obj instanceof double[]) {
            double[] array = (double[]) obj;
            List<Float> result = new ArrayList<>(array.length);
            for (double d : array) {
                result.add((float) d);
            }
            return result;
        } else {
            throw new IllegalArgumentException("Unsupported vector data type: " + obj.getClass());
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> convertToStringList(Object obj) {
        if (obj == null) {
            throw new IllegalArgumentException("Vector IDs cannot be null");
        }

        if (obj instanceof List) {
            return (List<String>) obj;
        } else if (obj instanceof String) {
            List<String> result = new ArrayList<>();
            result.add((String) obj);
            return result;
        } else if (obj instanceof String[]) {
            String[] array = (String[]) obj;
            List<String> result = new ArrayList<>(array.length);
            for (String s : array) {
                result.add(s);
            }
            return result;
        } else {
            throw new IllegalArgumentException("Unsupported vector ID type: " + obj.getClass());
        }
    }

    private Document convertMapToDocument(Map<String, String> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        Document.MapBuilder builder = Document.mapBuilder();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            builder.putString(entry.getKey(), entry.getValue());
        }
        return builder.build();
    }

    @Override
    public AWS2S3VectorsEndpoint getEndpoint() {
        return (AWS2S3VectorsEndpoint) super.getEndpoint();
    }

    private AWS2S3VectorsConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }
}
