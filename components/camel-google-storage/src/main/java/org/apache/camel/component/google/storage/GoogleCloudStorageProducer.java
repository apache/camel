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
package org.apache.camel.component.google.storage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.google.cloud.WriteChannel;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.CopyWriter;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.Storage.CopyRequest;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.WrappedFile;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GoogleCloudStorageProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(GoogleCloudStorageProducer.class);

    private GoogleCloudStorageEndpoint endpoint;

    public GoogleCloudStorageProducer(GoogleCloudStorageEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    public void process(Exchange exchange) throws Exception {

        GoogleCloudStorageOperations operation = determineOperation(exchange);
        if (ObjectHelper.isEmpty(operation)) {
            processFile(getEndpoint().getStorageClient(), exchange);
        } else {
            switch (operation) {
                case copyObject:
                    copyObject(getEndpoint().getStorageClient(), exchange);
                    break;
                case deleteObject:
                    deleteObject(getEndpoint().getStorageClient(), exchange);
                    break;
                case listBuckets:
                    listBuckets(getEndpoint().getStorageClient(), exchange);
                    break;
                case deleteBucket:
                    deleteBucket(getEndpoint().getStorageClient(), exchange);
                    break;
                case listObjects:
                    listObjects(getEndpoint().getStorageClient(), exchange);
                    break;
                case getObject:
                    getObject(getEndpoint().getStorageClient(), exchange);
                    break;
                case createDownloadLink:
                    createDownloadLink(getEndpoint().getStorageClient(), exchange);
                    break;

                default:
                    throw new IllegalArgumentException("Unsupported operation");
            }
        }

    }

    private void processFile(Storage storage, Exchange exchange) throws IOException, InvalidPayloadException {
        final String bucketName = determineBucketName(exchange);
        final String objectName = determineObjectName(exchange);

        Map<String, String> objectMetadata = determineMetadata(exchange);

        File filePayload;
        InputStream is;
        ByteArrayOutputStream baos = null;
        Object obj = exchange.getIn().getMandatoryBody();

        // Need to check if the message body is WrappedFile
        if (obj instanceof WrappedFile) {
            obj = ((WrappedFile<?>) obj).getFile();
        }
        if (obj instanceof File) {
            filePayload = (File) obj;
            is = new FileInputStream(filePayload);
        } else {
            is = exchange.getIn().getMandatoryBody(InputStream.class);
            baos = determineLengthInputStream(is);

            if (objectMetadata.containsKey(Exchange.CONTENT_LENGTH)) {
                if (objectMetadata.get("Content-Length").equals("0")
                        && ObjectHelper.isEmpty(exchange.getProperty(Exchange.CONTENT_LENGTH))) {
                    LOG.debug(
                            "The content length is not defined. It needs to be determined by reading the data into memory");
                    objectMetadata.put("Content-Length", String.valueOf(baos.size()));
                    is = new ByteArrayInputStream(baos.toByteArray());
                } else {
                    if (ObjectHelper.isNotEmpty(exchange.getProperty(Exchange.CONTENT_LENGTH))) {
                        objectMetadata.put("Content-Length",
                                exchange.getProperty(Exchange.CONTENT_LENGTH, String.class));
                    }
                }
            }
        }

        Blob createdBlob;
        BlobId blobId = BlobId.of(bucketName, objectName);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setMetadata(objectMetadata).build();
        if (baos.size() > 1_000_000) {
            // When content is not available or large (1MB or more) it is recommended
            // to write it in chunks via the blob's channel writer.
            try (WriteChannel writer = storage.writer(blobInfo)) {
                writer.write(ByteBuffer.wrap(baos.toByteArray()));
            }
            createdBlob = storage.get(blobId);
        } else {
            byte[] bytes = baos.toByteArray();
            // create the blob in one request.
            createdBlob = storage.create(blobInfo, bytes);
        }

        LOG.trace("created createdBlob [{}]", createdBlob);
        Message message = getMessageForResponse(exchange);
        message.setBody(createdBlob);

        IOHelper.close(baos);
        IOHelper.close(is);
    }

    private ByteArrayOutputStream determineLengthInputStream(InputStream is) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] bytes = new byte[1024];
        int count;
        while ((count = is.read(bytes)) > 0) {
            out.write(bytes, 0, count);
        }
        return out;
    }

    private Map<String, String> determineMetadata(final Exchange exchange) {
        Map<String, String> objectMetadata = new HashMap<String, String>();

        Long contentLength = exchange.getIn().getHeader(GoogleCloudStorageConstants.CONTENT_LENGTH, Long.class);
        if (contentLength != null) {
            objectMetadata.put("Content-Length", String.valueOf(contentLength));
        }

        String contentType = exchange.getIn().getHeader(GoogleCloudStorageConstants.CONTENT_TYPE, String.class);
        if (contentType != null) {
            objectMetadata.put("Content-Type", contentType);
        }

        String cacheControl = exchange.getIn().getHeader(GoogleCloudStorageConstants.CACHE_CONTROL, String.class);
        if (cacheControl != null) {
            objectMetadata.put("Cache-Control", cacheControl);
        }

        String contentDisposition = exchange.getIn().getHeader(GoogleCloudStorageConstants.CONTENT_DISPOSITION,
                String.class);
        if (contentDisposition != null) {
            objectMetadata.put("Content-Disposition", contentDisposition);
        }

        String contentEncoding = exchange.getIn().getHeader(GoogleCloudStorageConstants.CONTENT_ENCODING, String.class);
        if (contentEncoding != null) {
            objectMetadata.put("Content-Encoding", contentEncoding);
        }

        String contentMD5 = exchange.getIn().getHeader(GoogleCloudStorageConstants.CONTENT_MD5, String.class);
        if (contentMD5 != null) {
            objectMetadata.put("Content-Md5", contentMD5);
        }

        return objectMetadata;
    }

    private void createDownloadLink(Storage storage, Exchange exchange) {
        final String bucketName = determineBucketName(exchange);
        final String objectName = determineObjectName(exchange);
        Long expirationMillis
                = exchange.getIn().getHeader(GoogleCloudStorageConstants.DOWNLOAD_LINK_EXPIRATION_TIME, 300000L, Long.class);
        long milliSeconds = 0;
        if (expirationMillis != null) {
            milliSeconds += expirationMillis;
        } else {
            milliSeconds += 1000 * 60 * 60;
        }

        BlobId blobId = BlobId.of(bucketName, objectName);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();
        URL url = storage.signUrl(blobInfo, milliSeconds, TimeUnit.MILLISECONDS);

        Message message = getMessageForResponse(exchange);
        message.setBody(url.toString());

    }

    private void copyObject(Storage storage, Exchange exchange) {
        final String bucketName = determineBucketName(exchange);
        final String objectName = determineObjectName(exchange);
        final String destinationObjectName = exchange.getIn()
                .getHeader(GoogleCloudStorageConstants.DESTINATION_OBJECT_NAME, String.class);
        final String bucketNameDestination = exchange.getIn()
                .getHeader(GoogleCloudStorageConstants.DESTINATION_BUCKET_NAME, String.class);

        if (ObjectHelper.isEmpty(bucketNameDestination)) {
            throw new IllegalArgumentException("Bucket Name Destination must be specified for copyObject Operation");
        }
        if (ObjectHelper.isEmpty(destinationObjectName)) {
            throw new IllegalArgumentException("Destination Key must be specified for copyObject Operation");
        }

        //if bucket does not exsist, create it
        Bucket destinationBucketCheck = storage.get(bucketNameDestination);
        if (destinationBucketCheck != null) {
            LOG.trace("destinationBucketCheck [{}] already exists", destinationBucketCheck.getName());
        } else {
            LOG.trace("Destination Bucket [{}] doesn't exist yet", bucketNameDestination);
            if (getConfiguration().isAutoCreateBucket()) {
                // creates the new bucket because it doesn't exist yet
                destinationBucketCheck
                        = GoogleCloudStorageEndpoint.createNewBucket(bucketNameDestination, getConfiguration(), storage);
            }
        }

        BlobId sourceBlobId = BlobId.of(bucketName, objectName);
        BlobId targetBlobId = BlobId.of(bucketNameDestination, destinationObjectName);
        CopyRequest request = CopyRequest.of(sourceBlobId, targetBlobId);
        CopyWriter copyWriter = storage.copy(request);

        Message message = getMessageForResponse(exchange);
        message.setBody(copyWriter);

    }

    private void deleteObject(Storage storage, Exchange exchange) {
        final String bucketName = determineBucketName(exchange);
        final String objectName = determineObjectName(exchange);

        BlobId blobId = BlobId.of(bucketName, objectName);
        boolean result = storage.delete(blobId);
        Message message = getMessageForResponse(exchange);
        message.setBody(result);

    }

    private void deleteBucket(Storage storage, Exchange exchange) {
        final String bucketName = determineBucketName(exchange);

        for (Blob blob : storage.list(bucketName).iterateAll()) {
            storage.delete(blob.getBlobId());
        }

        boolean result = storage.delete(bucketName);
        Message message = getMessageForResponse(exchange);
        message.setBody(result);

    }

    private void listBuckets(Storage storage, Exchange exchange) {
        List<Bucket> bucketsList = new LinkedList<>();
        for (Bucket bucket : storage.list().iterateAll()) {
            bucketsList.add(bucket);
        }

        Message message = getMessageForResponse(exchange);
        message.setBody(bucketsList);
    }

    private void getObject(Storage storage, Exchange exchange) {
        final String bucketName = determineBucketName(exchange);
        final String objectName = determineObjectName(exchange);

        Blob blob = storage.get(BlobId.of(bucketName, objectName));
        Message message = getMessageForResponse(exchange);
        message.setBody(blob);

    }

    private void listObjects(Storage storage, Exchange exchange) {
        final String bucketName = determineBucketName(exchange);

        List<Blob> bloblist = new LinkedList<>();
        for (Blob blob : storage.list(bucketName).iterateAll()) {
            bloblist.add(blob);
        }

        Message message = getMessageForResponse(exchange);
        message.setBody(bloblist);

    }

    private String determineObjectName(Exchange exchange) {
        String key = exchange.getIn().getHeader(GoogleCloudStorageConstants.OBJECT_NAME, String.class);
        if (ObjectHelper.isEmpty(key)) {
            key = getConfiguration().getObjectName();
        }
        if (key == null) {
            throw new IllegalArgumentException("Google Cloud Storage object name header missing.");
        }
        return key;
    }

    private String determineBucketName(Exchange exchange) {
        String bucketName = getConfiguration().getBucketName();
        if (bucketName == null) {
            throw new IllegalArgumentException("Bucket name is missing or not configured.");
        }
        return bucketName;
    }

    public static Message getMessageForResponse(final Exchange exchange) {
        return exchange.getMessage();
    }

    private GoogleCloudStorageOperations determineOperation(Exchange exchange) {
        GoogleCloudStorageOperations operation = exchange.getIn().getHeader(
                GoogleCloudStorageConstants.OPERATION,
                GoogleCloudStorageOperations.class);
        if (operation == null) {
            operation = getConfiguration().getOperation();
        }
        return operation;
    }

    @Override
    public GoogleCloudStorageEndpoint getEndpoint() {
        return (GoogleCloudStorageEndpoint) super.getEndpoint();
    }

    private GoogleCloudStorageConfiguration getConfiguration() {
        return this.endpoint.getConfiguration();
    }

}
