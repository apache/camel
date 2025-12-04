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

package org.apache.camel.component.ibm.cos;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.ibm.cloud.objectstorage.services.s3.AmazonS3;
import com.ibm.cloud.objectstorage.services.s3.model.CopyObjectRequest;
import com.ibm.cloud.objectstorage.services.s3.model.DeleteObjectRequest;
import com.ibm.cloud.objectstorage.services.s3.model.DeleteObjectsRequest;
import com.ibm.cloud.objectstorage.services.s3.model.DeleteObjectsResult;
import com.ibm.cloud.objectstorage.services.s3.model.GetObjectRequest;
import com.ibm.cloud.objectstorage.services.s3.model.ListObjectsV2Request;
import com.ibm.cloud.objectstorage.services.s3.model.ListObjectsV2Result;
import com.ibm.cloud.objectstorage.services.s3.model.ObjectMetadata;
import com.ibm.cloud.objectstorage.services.s3.model.PutObjectRequest;
import com.ibm.cloud.objectstorage.services.s3.model.PutObjectResult;
import com.ibm.cloud.objectstorage.services.s3.model.S3Object;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The IBM COS producer.
 */
public class IBMCOSProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(IBMCOSProducer.class);

    public IBMCOSProducer(IBMCOSEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        IBMCOSOperations operation = determineOperation(exchange);
        if (ObjectHelper.isEmpty(operation)) {
            operation = IBMCOSOperations.putObject;
        }

        switch (operation) {
            case copyObject:
                copyObject(getEndpoint().getCosClient(), exchange);
                break;
            case deleteObject:
                deleteObject(getEndpoint().getCosClient(), exchange);
                break;
            case deleteObjects:
                deleteObjects(getEndpoint().getCosClient(), exchange);
                break;
            case listBuckets:
                listBuckets(getEndpoint().getCosClient(), exchange);
                break;
            case deleteBucket:
                deleteBucket(getEndpoint().getCosClient(), exchange);
                break;
            case listObjects:
                listObjects(getEndpoint().getCosClient(), exchange);
                break;
            case getObject:
                getObject(getEndpoint().getCosClient(), exchange);
                break;
            case getObjectRange:
                getObjectRange(getEndpoint().getCosClient(), exchange);
                break;
            case createBucket:
                createBucket(getEndpoint().getCosClient(), exchange);
                break;
            case headBucket:
                headBucket(getEndpoint().getCosClient(), exchange);
                break;
            case putObject:
                putObject(getEndpoint().getCosClient(), exchange);
                break;
            default:
                throw new IllegalArgumentException("Unsupported operation: " + operation);
        }
    }

    private IBMCOSOperations determineOperation(Exchange exchange) {
        IBMCOSOperations operation = exchange.getIn().getHeader(IBMCOSConstants.COS_OPERATION, IBMCOSOperations.class);
        if (operation == null) {
            operation = getConfiguration().getOperation();
        }
        return operation;
    }

    private void putObject(AmazonS3 cosClient, Exchange exchange) throws Exception {
        String bucketName = determineBucketName(exchange);
        String key = determineKey(exchange);

        ObjectMetadata metadata = new ObjectMetadata();

        // Set metadata from headers
        Map<String, String> userMetadata = exchange.getIn().getHeader(IBMCOSConstants.METADATA, Map.class);
        if (userMetadata != null) {
            metadata.setUserMetadata(userMetadata);
        }

        String contentType = exchange.getIn().getHeader(IBMCOSConstants.CONTENT_TYPE, String.class);
        if (contentType != null) {
            metadata.setContentType(contentType);
        }

        InputStream inputStream = exchange.getIn().getMandatoryBody(InputStream.class);

        // Calculate content length if not provided to avoid SDK warnings
        Long contentLength = exchange.getIn().getHeader(IBMCOSConstants.CONTENT_LENGTH, Long.class);
        if (contentLength != null) {
            metadata.setContentLength(contentLength);
        } else if (inputStream.markSupported()) {
            // For ByteArrayInputStream and similar streams that support mark/reset
            inputStream.mark(Integer.MAX_VALUE);
            long length = 0;
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                length += read;
            }
            inputStream.reset();
            metadata.setContentLength(length);
        }

        PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, key, inputStream, metadata);

        LOG.trace("Putting object [{}] into bucket [{}]...", key, bucketName);
        PutObjectResult putObjectResult = cosClient.putObject(putObjectRequest);

        Message message = getMessageForResponse(exchange);
        message.setHeader(IBMCOSConstants.E_TAG, putObjectResult.getETag());
        message.setHeader(IBMCOSConstants.VERSION_ID, putObjectResult.getVersionId());
    }

    private void getObject(AmazonS3 cosClient, Exchange exchange) {
        String bucketName = determineBucketName(exchange);
        String key = determineKey(exchange);

        LOG.trace("Getting object [{}] from bucket [{}]...", key, bucketName);

        GetObjectRequest getObjectRequest = new GetObjectRequest(bucketName, key);
        S3Object s3Object = cosClient.getObject(getObjectRequest);

        Message message = getMessageForResponse(exchange);
        message.setBody(s3Object.getObjectContent());
        message.setHeader(IBMCOSConstants.E_TAG, s3Object.getObjectMetadata().getETag());
        message.setHeader(
                IBMCOSConstants.CONTENT_TYPE, s3Object.getObjectMetadata().getContentType());
        message.setHeader(
                IBMCOSConstants.CONTENT_LENGTH, s3Object.getObjectMetadata().getContentLength());
        message.setHeader(
                IBMCOSConstants.LAST_MODIFIED, s3Object.getObjectMetadata().getLastModified());
    }

    private void getObjectRange(AmazonS3 cosClient, Exchange exchange) {
        String bucketName = determineBucketName(exchange);
        String key = determineKey(exchange);

        Long rangeStart = exchange.getIn().getHeader(IBMCOSConstants.RANGE_START, Long.class);
        Long rangeEnd = exchange.getIn().getHeader(IBMCOSConstants.RANGE_END, Long.class);

        if (rangeStart == null || rangeEnd == null) {
            throw new IllegalArgumentException("Range start and end must be specified");
        }

        LOG.trace("Getting object range [{}] from bucket [{}]...", key, bucketName);

        GetObjectRequest getObjectRequest = new GetObjectRequest(bucketName, key).withRange(rangeStart, rangeEnd);
        S3Object s3Object = cosClient.getObject(getObjectRequest);

        Message message = getMessageForResponse(exchange);
        message.setBody(s3Object.getObjectContent());
    }

    private void deleteObject(AmazonS3 cosClient, Exchange exchange) {
        String bucketName = determineBucketName(exchange);
        String key = determineKey(exchange);

        LOG.trace("Deleting object [{}] from bucket [{}]...", key, bucketName);
        cosClient.deleteObject(new DeleteObjectRequest(bucketName, key));
    }

    private void deleteObjects(AmazonS3 cosClient, Exchange exchange) {
        String bucketName = determineBucketName(exchange);
        List<String> keys = exchange.getIn().getHeader(IBMCOSConstants.KEYS_TO_DELETE, List.class);

        if (keys == null || keys.isEmpty()) {
            throw new IllegalArgumentException("Keys to delete must be specified");
        }

        LOG.trace("Deleting {} objects from bucket [{}]...", keys.size(), bucketName);

        // Create KeyVersion objects for each key
        List<DeleteObjectsRequest.KeyVersion> keyVersions = new ArrayList<>();
        for (String key : keys) {
            keyVersions.add(new DeleteObjectsRequest.KeyVersion(key));
        }

        DeleteObjectsRequest deleteObjectsRequest = new DeleteObjectsRequest(bucketName).withKeys(keyVersions);

        DeleteObjectsResult result = cosClient.deleteObjects(deleteObjectsRequest);

        Message message = getMessageForResponse(exchange);
        message.setBody(result.getDeletedObjects());
    }

    private void listObjects(AmazonS3 cosClient, Exchange exchange) {
        String bucketName = determineBucketName(exchange);

        ListObjectsV2Request request = new ListObjectsV2Request().withBucketName(bucketName);

        String prefix = exchange.getIn().getHeader(IBMCOSConstants.PREFIX, String.class);
        if (prefix != null) {
            request.withPrefix(prefix);
        }

        String delimiter = exchange.getIn().getHeader(IBMCOSConstants.DELIMITER, String.class);
        if (delimiter != null) {
            request.withDelimiter(delimiter);
        }

        LOG.trace("Listing objects in bucket [{}]...", bucketName);
        ListObjectsV2Result result = cosClient.listObjectsV2(request);

        Message message = getMessageForResponse(exchange);
        message.setBody(result.getObjectSummaries());
    }

    private void listBuckets(AmazonS3 cosClient, Exchange exchange) {
        LOG.trace("Listing buckets...");
        Message message = getMessageForResponse(exchange);
        message.setBody(cosClient.listBuckets());
    }

    private void deleteBucket(AmazonS3 cosClient, Exchange exchange) {
        String bucketName = determineBucketName(exchange);
        LOG.trace("Deleting bucket [{}]...", bucketName);
        cosClient.deleteBucket(bucketName);
    }

    private void copyObject(AmazonS3 cosClient, Exchange exchange) {
        String bucketName = determineBucketName(exchange);
        String key = determineKey(exchange);
        String destinationBucket = exchange.getIn().getHeader(IBMCOSConstants.BUCKET_DESTINATION_NAME, String.class);
        String destinationKey = exchange.getIn().getHeader(IBMCOSConstants.DESTINATION_KEY, String.class);

        if (destinationBucket == null) {
            throw new IllegalArgumentException("Destination bucket must be specified");
        }
        if (destinationKey == null) {
            throw new IllegalArgumentException("Destination key must be specified");
        }

        LOG.trace(
                "Copying object [{}] from bucket [{}] to bucket [{}] with key [{}]...",
                key,
                bucketName,
                destinationBucket,
                destinationKey);

        CopyObjectRequest copyObjectRequest = new CopyObjectRequest(bucketName, key, destinationBucket, destinationKey);
        cosClient.copyObject(copyObjectRequest);
    }

    private void createBucket(AmazonS3 cosClient, Exchange exchange) {
        String bucketName = determineBucketName(exchange);
        LOG.trace("Creating bucket [{}]...", bucketName);
        // The location is specified via the client's endpoint configuration
        cosClient.createBucket(bucketName);
    }

    private void headBucket(AmazonS3 cosClient, Exchange exchange) {
        String bucketName = determineBucketName(exchange);
        LOG.trace("Checking if bucket [{}] exists...", bucketName);

        boolean exists = cosClient.doesBucketExistV2(bucketName);

        Message message = getMessageForResponse(exchange);
        message.setBody(exists);
    }

    private String determineBucketName(Exchange exchange) {
        String bucketName = exchange.getIn().getHeader(IBMCOSConstants.BUCKET_NAME, String.class);
        if (bucketName == null) {
            bucketName = getConfiguration().getBucketName();
        }
        return bucketName;
    }

    private String determineKey(Exchange exchange) {
        String key = exchange.getIn().getHeader(IBMCOSConstants.KEY, String.class);
        if (key == null) {
            key = getConfiguration().getKeyName();
        }
        if (key == null) {
            throw new IllegalArgumentException("Key must be specified");
        }
        return key;
    }

    protected IBMCOSConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    @Override
    public IBMCOSEndpoint getEndpoint() {
        return (IBMCOSEndpoint) super.getEndpoint();
    }

    public static Message getMessageForResponse(final Exchange exchange) {
        return exchange.getMessage();
    }
}
