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
package org.apache.camel.component.minio;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.minio.CopyObjectArgs;
import io.minio.CopySource;
import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.PutObjectArgs;
import io.minio.RemoveBucketArgs;
import io.minio.RemoveObjectArgs;
import io.minio.RemoveObjectsArgs;
import io.minio.Result;
import io.minio.messages.Bucket;
import io.minio.messages.Item;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.WrappedFile;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.util.ObjectHelper.isEmpty;
import static org.apache.camel.util.ObjectHelper.isNotEmpty;

/**
 * A Producer which sends messages to the Minio Simple Storage
 */
public class MinioProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(MinioProducer.class);

    private transient String minioProducerToString;

    public MinioProducer(final Endpoint endpoint) {
        super(endpoint);
    }

    public static Message getMessageForResponse(final Exchange exchange) {
        return exchange.getMessage();
    }

    @Override
    public void process(final Exchange exchange) throws Exception {
        MinioOperations operation = determineOperation(exchange);
        MinioClient minioClient = getEndpoint().getMinioClient();
        if (isEmpty(operation)) {
            putObject(minioClient, exchange);
        } else {
            switch (operation) {
                case copyObject:
                    copyObject(minioClient, exchange);
                    break;
                case deleteObject:
                    deleteObject(minioClient, exchange);
                    break;
                case deleteObjects:
                    deleteObjects(minioClient, exchange);
                    break;
                case listBuckets:
                    listBuckets(minioClient, exchange);
                    break;
                case deleteBucket:
                    deleteBucket(minioClient, exchange);
                    break;
                case listObjects:
                    listObjects(minioClient, exchange);
                    break;
                case getObject:
                    getObject(minioClient, exchange);
                    break;
                case getPartialObject:
                    getPartialObject(minioClient, exchange);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported operation");
            }
        }
    }

    public void putObject(MinioClient minioClient, final Exchange exchange) throws Exception {

        if (getConfiguration().isPojoRequest()) {
            PutObjectArgs.Builder payload = exchange.getIn().getMandatoryBody(PutObjectArgs.Builder.class);
            if (isNotEmpty(payload)) {
                ObjectWriteResponse putObjectResult = minioClient.putObject(payload.build());
                Message message = getMessageForResponse(exchange);
                message.setHeader(MinioConstants.E_TAG, putObjectResult.etag());
                if (isNotEmpty(putObjectResult.versionId())) {
                    message.setHeader(MinioConstants.VERSION_ID, putObjectResult.versionId());
                }
            }
        } else {
            final String bucketName = determineBucketName(exchange);
            final String objectName = determineObjectName(exchange);
            Map<String, String> objectMetadata = determineMetadata(exchange);
            Map<String, String> extraHeaders = determineExtraHeaders(exchange);

            File filePayload = null;
            InputStream inputStream;
            ByteArrayOutputStream baos;
            Object object = exchange.getIn().getMandatoryBody();

            // Need to check if the message body is WrappedFile
            if (object instanceof WrappedFile) {
                object = ((WrappedFile<?>) object).getFile();
            }
            if (object instanceof File) {
                filePayload = (File) object;
                inputStream = new FileInputStream(filePayload);
            } else {
                inputStream = exchange.getIn().getMandatoryBody(InputStream.class);
                if (objectMetadata.containsKey(Exchange.CONTENT_LENGTH)) {
                    if (objectMetadata.get("Content-Length").equals("0")
                            && isEmpty(exchange.getProperty(Exchange.CONTENT_LENGTH))) {
                        LOG.debug(
                                "The content length is not defined. It needs to be determined by reading the data into memory");
                        baos = determineLengthInputStream(inputStream);
                        objectMetadata.put("Content-Length", String.valueOf(baos.size()));
                        inputStream = new ByteArrayInputStream(baos.toByteArray());
                    } else {
                        if (isNotEmpty(exchange.getProperty(Exchange.CONTENT_LENGTH))) {
                            objectMetadata.put("Content-Length", exchange.getProperty(Exchange.CONTENT_LENGTH, String.class));
                        }
                    }
                }
            }
            PutObjectArgs.Builder putObjectRequest = PutObjectArgs.builder()
                    .stream(inputStream, inputStream.available(), -1)
                    .bucket(bucketName)
                    .object(objectName)
                    .userMetadata(objectMetadata);

            if (!extraHeaders.isEmpty()) {
                putObjectRequest.extraHeaders(extraHeaders);
            }

            LOG.trace("Put object from exchange...");

            ObjectWriteResponse putObjectResult = getEndpoint().getMinioClient().putObject(putObjectRequest.build());

            LOG.trace("Received result...");

            Message message = getMessageForResponse(exchange);
            message.setHeader(MinioConstants.E_TAG, putObjectResult.etag());
            if (isNotEmpty(putObjectResult.versionId())) {
                message.setHeader(MinioConstants.VERSION_ID, putObjectResult.versionId());
            }

            IOHelper.close(inputStream);

            if (getConfiguration().isDeleteAfterWrite() && isNotEmpty(filePayload)) {
                FileUtil.deleteFile(filePayload);
            }
        }
    }

    private Map<String, String> determineExtraHeaders(Exchange exchange) {
        Map<String, String> extraHeaders = new HashMap<>();
        String storageClass = determineStorageClass(exchange);
        if (isNotEmpty(storageClass)) {
            extraHeaders.put("X-Amz-Storage-Class", storageClass);
        }

        String cannedAcl = exchange.getIn().getHeader(MinioConstants.CANNED_ACL, String.class);
        if (isNotEmpty(cannedAcl)) {
            extraHeaders.put("x-amz-acl", cannedAcl);
        }

        return extraHeaders;
    }

    private void copyObject(MinioClient minioClient, Exchange exchange) throws Exception {

        if (getConfiguration().isPojoRequest()) {
            CopyObjectArgs.Builder payload = exchange.getIn().getMandatoryBody(CopyObjectArgs.Builder.class);
            if (isNotEmpty(payload)) {
                ObjectWriteResponse result = minioClient.copyObject(payload.build());
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {

            final String bucketName = determineBucketName(exchange);
            final String sourceKey = determineObjectName(exchange);
            final String destinationKey = exchange.getIn().getHeader(MinioConstants.DESTINATION_OBJECT_NAME, String.class);
            final String destinationBucketName
                    = exchange.getIn().getHeader(MinioConstants.DESTINATION_BUCKET_NAME, String.class);

            if (isEmpty(destinationBucketName)) {
                throw new IllegalArgumentException("Bucket Name Destination must be specified for copyObject Operation");
            }
            if (isEmpty(destinationKey)) {
                throw new IllegalArgumentException("Destination Key must be specified for copyObject Operation");
            }

            CopySource.Builder copySourceBuilder = CopySource.builder()
                    .bucket(bucketName)
                    .object(sourceKey);

            CopyObjectArgs.Builder copyObjectRequest = CopyObjectArgs.builder()
                    .bucket(destinationBucketName)
                    .object(destinationKey)
                    .source(copySourceBuilder.build());

            ObjectWriteResponse copyObjectResult = minioClient.copyObject(copyObjectRequest.build());

            Message message = getMessageForResponse(exchange);
            if (isNotEmpty(copyObjectResult.versionId())) {
                message.setHeader(MinioConstants.VERSION_ID, copyObjectResult.versionId());
            }
        }
    }

    private void deleteObject(MinioClient minioClient, Exchange exchange) throws Exception {
        if (getConfiguration().isPojoRequest()) {
            RemoveObjectArgs.Builder payload = exchange.getIn().getMandatoryBody(RemoveObjectArgs.Builder.class);
            if (isNotEmpty(payload)) {
                minioClient.removeObject(payload.build());
                Message message = getMessageForResponse(exchange);
                message.setBody(true);
            }
        } else {
            final String bucketName = determineBucketName(exchange);
            final String sourceKey = determineObjectName(exchange);
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucketName)
                    .object(sourceKey).build());

            Message message = getMessageForResponse(exchange);
            message.setBody(true);
        }
    }

    private void deleteObjects(MinioClient minioClient, Exchange exchange) throws Exception {
        if (getConfiguration().isPojoRequest()) {
            RemoveObjectsArgs.Builder payload = exchange.getIn().getMandatoryBody(RemoveObjectsArgs.Builder.class);
            if (isNotEmpty(payload)) {
                minioClient.removeObjects(payload.build());
                Message message = getMessageForResponse(exchange);
                message.setBody(true);
            }
        } else {
            throw new IllegalArgumentException("Cannot delete multiple objects without a POJO request");
        }
    }

    private void listBuckets(MinioClient minioClient, Exchange exchange) throws Exception {
        List<Bucket> bucketsList = minioClient.listBuckets();
        Message message = getMessageForResponse(exchange);
        //returns iterator of bucketList
        message.setBody(bucketsList.iterator());
    }

    private void deleteBucket(MinioClient minioClient, Exchange exchange) throws Exception {
        final String bucketName = determineBucketName(exchange);

        if (getConfiguration().isPojoRequest()) {
            RemoveBucketArgs.Builder payload = exchange.getIn().getMandatoryBody(RemoveBucketArgs.Builder.class);
            if (isNotEmpty(payload)) {
                minioClient.removeBucket(payload.build());
                Message message = getMessageForResponse(exchange);
                message.setBody("ok");
            }
        } else {

            minioClient.removeBucket(RemoveBucketArgs.builder().bucket(bucketName).build());
            Message message = getMessageForResponse(exchange);
            message.setBody("ok");
        }
    }

    private void getObject(MinioClient minioClient, Exchange exchange) throws Exception {

        if (getConfiguration().isPojoRequest()) {
            GetObjectArgs.Builder payload = exchange.getIn().getMandatoryBody(GetObjectArgs.Builder.class);
            if (isNotEmpty(payload)) {
                InputStream respond = minioClient.getObject(payload.build());
                Message message = getMessageForResponse(exchange);
                message.setBody(respond);
            }
        } else {
            final String bucketName = determineBucketName(exchange);
            final String sourceKey = determineObjectName(exchange);

            InputStream respond = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucketName)
                    .object(sourceKey)
                    .build());

            Message message = getMessageForResponse(exchange);
            message.setBody(respond);
        }
    }

    private void getPartialObject(MinioClient minioClient, Exchange exchange) throws Exception {

        if (getConfiguration().isPojoRequest()) {
            GetObjectArgs.Builder payload = exchange.getIn().getMandatoryBody(GetObjectArgs.Builder.class);
            if (isNotEmpty(payload)) {
                InputStream respond = minioClient.getObject(payload.build());
                Message message = getMessageForResponse(exchange);
                message.setBody(respond);
            }
        } else {
            final String bucketName = determineBucketName(exchange);
            final String sourceKey = determineObjectName(exchange);
            final String offset = exchange.getIn().getHeader(MinioConstants.OFFSET, String.class);
            final String length = exchange.getIn().getHeader(MinioConstants.LENGTH, String.class);

            if (isEmpty(offset) || isEmpty(length)) {
                throw new IllegalArgumentException(
                        "A Offset and length header must be configured to perform a partial get operation.");
            }

            InputStream respond = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucketName)
                    .object(sourceKey)
                    .offset(Long.parseLong(offset))
                    .length(Long.parseLong(length))
                    .build());

            Message message = getMessageForResponse(exchange);
            message.setBody(respond);
        }
    }

    private void listObjects(MinioClient minioClient, Exchange exchange) throws InvalidPayloadException {

        if (getConfiguration().isPojoRequest()) {
            ListObjectsArgs.Builder payload = exchange.getIn().getMandatoryBody(ListObjectsArgs.Builder.class);
            if (isNotEmpty(payload)) {
                Iterable<Result<Item>> objectList = minioClient.listObjects(payload.build());
                Message message = getMessageForResponse(exchange);
                message.setBody(objectList);
            }
        } else {
            final String bucketName = determineBucketName(exchange);

            Iterable<Result<Item>> objectList = minioClient.listObjects(ListObjectsArgs.builder()
                    .bucket(bucketName)
                    .build());

            Message message = getMessageForResponse(exchange);
            message.setBody(objectList);
        }
    }

    private MinioOperations determineOperation(Exchange exchange) {
        MinioOperations operation = exchange.getIn().getHeader(MinioConstants.MINIO_OPERATION, MinioOperations.class);
        if (isEmpty(operation)) {
            operation = getConfiguration().getOperation();
        }
        return operation;
    }

    private Map<String, String> determineMetadata(final Exchange exchange) {
        Map<String, String> objectMetadata = new HashMap<>();

        Long contentLength = exchange.getIn().getHeader(MinioConstants.CONTENT_LENGTH, Long.class);
        if (isNotEmpty(contentLength)) {
            objectMetadata.put("Content-Length", String.valueOf(contentLength));
        }

        String contentType = exchange.getIn().getHeader(MinioConstants.CONTENT_TYPE, String.class);
        if (isNotEmpty(contentType)) {
            objectMetadata.put("Content-Type", contentType);
        }

        String cacheControl = exchange.getIn().getHeader(MinioConstants.CACHE_CONTROL, String.class);
        if (isNotEmpty(cacheControl)) {
            objectMetadata.put("Cache-Control", cacheControl);
        }

        String contentDisposition = exchange.getIn().getHeader(MinioConstants.CONTENT_DISPOSITION, String.class);
        if (isNotEmpty(contentDisposition)) {
            objectMetadata.put("Content-Disposition", contentDisposition);
        }

        String contentEncoding = exchange.getIn().getHeader(MinioConstants.CONTENT_ENCODING, String.class);
        if (isNotEmpty(contentEncoding)) {
            objectMetadata.put("Content-Encoding", contentEncoding);
        }

        String contentMD5 = exchange.getIn().getHeader(MinioConstants.CONTENT_MD5, String.class);
        if (isNotEmpty(contentMD5)) {
            objectMetadata.put("Content-Md5", contentMD5);
        }

        return objectMetadata;
    }

    /**
     * Reads the bucket name from the header of the given exchange. If not provided, it's read from the endpoint
     * configuration.
     *
     * @param  exchange                 The exchange to read the header from.
     * @return                          The bucket name.
     * @throws IllegalArgumentException if the header could not be determined.
     */
    private String determineBucketName(final Exchange exchange) {
        String bucketName = exchange.getIn().getHeader(MinioConstants.BUCKET_NAME, String.class);

        if (isEmpty(bucketName)) {
            if (isNotEmpty(getConfiguration().getBucketName())) {
                bucketName = getConfiguration().getBucketName();
                LOG.trace("Minio Bucket name header is missing, using default one {}", bucketName);
            } else {
                throw new IllegalArgumentException("Minio Bucket name header is missing or not configured.");
            }
        }
        return bucketName;
    }

    private String determineObjectName(final Exchange exchange) {
        String objectName = exchange.getIn().getHeader(MinioConstants.OBJECT_NAME, String.class);
        if (isEmpty(objectName)) {
            objectName = getConfiguration().getKeyName();
        }
        if (isEmpty(objectName)) {
            throw new IllegalArgumentException("Minio Key header is missing.");
        }
        return objectName;
    }

    private String determineStorageClass(final Exchange exchange) {
        String storageClass = exchange.getIn().getHeader(MinioConstants.STORAGE_CLASS, String.class);
        if (isEmpty(storageClass)) {
            storageClass = getConfiguration().getStorageClass();
        }

        return storageClass;
    }

    private ByteArrayOutputStream determineLengthInputStream(InputStream inputStream) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] bytes = new byte[MinioConstants.BYTE_ARRAY_LENGTH];
        int count;
        while ((count = inputStream.read(bytes)) > 0) {
            out.write(bytes, 0, count);
        }
        return out;
    }

    protected MinioConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    @Override
    public String toString() {
        if (isEmpty(minioProducerToString)) {
            minioProducerToString = "MinioProducer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
        }
        return minioProducerToString;
    }

    @Override
    public MinioEndpoint getEndpoint() {
        return (MinioEndpoint) super.getEndpoint();
    }
}
