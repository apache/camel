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
package org.apache.camel.component.aws2.s3;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.WrappedFile;
import org.apache.camel.component.aws2.s3.utils.AWS2S3Utils;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

/**
 * A Producer which sends messages to the Amazon Web Service Simple Storage Service
 * <a href="http://aws.amazon.com/s3/">AWS S3</a>
 */
public class AWS2S3Producer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(AWS2S3Producer.class);

    public AWS2S3Producer(final Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(final Exchange exchange) throws Exception {
        AWS2S3Operations operation = determineOperation(exchange);
        if (ObjectHelper.isEmpty(operation)) {
            if (getConfiguration().isMultiPartUpload()) {
                processMultiPart(exchange);
            } else {
                processSingleOp(exchange);
            }
        } else {
            switch (operation) {
                case copyObject:
                    copyObject(getEndpoint().getS3Client(), exchange);
                    break;
                case deleteObject:
                    deleteObject(getEndpoint().getS3Client(), exchange);
                    break;
                case deleteObjects:
                    deleteObjects(getEndpoint().getS3Client(), exchange);
                    break;
                case listBuckets:
                    listBuckets(getEndpoint().getS3Client(), exchange);
                    break;
                case deleteBucket:
                    deleteBucket(getEndpoint().getS3Client(), exchange);
                    break;
                case listObjects:
                    listObjects(getEndpoint().getS3Client(), exchange);
                    break;
                case getObject:
                    getObject(getEndpoint().getS3Client(), exchange);
                    break;
                case getObjectRange:
                    getObjectRange(getEndpoint().getS3Client(), exchange);
                    break;
                case createDownloadLink:
                    createDownloadLink(exchange);
                    break;
                case createUploadLink:
                    createUploadLink(exchange);
                    break;
                case headBucket:
                    headBucket(getEndpoint().getS3Client(), exchange);
                    break;
                case headObject:
                    headObject(getEndpoint().getS3Client(), exchange);
                    break;
                case restoreObject:
                    restoreObject(getEndpoint().getS3Client(), exchange);
                    break;
                case getObjectTagging:
                    getObjectTagging(getEndpoint().getS3Client(), exchange);
                    break;
                case putObjectTagging:
                    putObjectTagging(getEndpoint().getS3Client(), exchange);
                    break;
                case deleteObjectTagging:
                    deleteObjectTagging(getEndpoint().getS3Client(), exchange);
                    break;
                case getObjectAcl:
                    getObjectAcl(getEndpoint().getS3Client(), exchange);
                    break;
                case putObjectAcl:
                    putObjectAcl(getEndpoint().getS3Client(), exchange);
                    break;
                case createBucket:
                    createBucket(getEndpoint().getS3Client(), exchange);
                    break;
                case getBucketTagging:
                    getBucketTagging(getEndpoint().getS3Client(), exchange);
                    break;
                case putBucketTagging:
                    putBucketTagging(getEndpoint().getS3Client(), exchange);
                    break;
                case deleteBucketTagging:
                    deleteBucketTagging(getEndpoint().getS3Client(), exchange);
                    break;
                case getBucketVersioning:
                    getBucketVersioning(getEndpoint().getS3Client(), exchange);
                    break;
                case putBucketVersioning:
                    putBucketVersioning(getEndpoint().getS3Client(), exchange);
                    break;
                case getBucketPolicy:
                    getBucketPolicy(getEndpoint().getS3Client(), exchange);
                    break;
                case putBucketPolicy:
                    putBucketPolicy(getEndpoint().getS3Client(), exchange);
                    break;
                case deleteBucketPolicy:
                    deleteBucketPolicy(getEndpoint().getS3Client(), exchange);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported operation");
            }
        }
    }

    public void processMultiPart(final Exchange exchange) throws Exception {
        Object obj = exchange.getIn().getBody();
        InputStream inputStream;
        File filePayload = null;

        // the content-length may already be known
        long contentLength = exchange.getIn().getHeader(AWS2S3Constants.CONTENT_LENGTH, -1, Long.class);

        // Need to check if the message body is WrappedFile
        if (obj instanceof WrappedFile<?> wf) {
            obj = wf.getFile();
        }
        if (obj instanceof File f) {
            filePayload = f;
            inputStream = new FileInputStream(f);
            contentLength = f.length();
        } else {
            // okay we use input stream
            inputStream = exchange.getIn().getMandatoryBody(InputStream.class);
            if (contentLength <= 0) {
                contentLength = AWS2S3Utils.determineLengthInputStream(inputStream);
                if (contentLength == -1) {
                    // fallback to read into memory to calculate length
                    LOG.debug(
                            "The content length is not defined. It needs to be determined by reading the data into memory");
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    IOHelper.copyAndCloseInput(inputStream, baos);
                    byte[] arr = baos.toByteArray();
                    contentLength = arr.length;
                    inputStream = new ByteArrayInputStream(arr);
                }
            }
        }

        Map<String, String> objectMetadata = determineMetadata(exchange);

        long partSize = getConfiguration().getPartSize();
        if (contentLength == 0 || contentLength < partSize) {
            // optimize to do a single op if content length is known and < part size
            LOG.debug("Payload size < partSize ({} > {}). Uploading payload in single operation", contentLength, partSize);
            doPutObject(exchange, objectMetadata, filePayload, inputStream, contentLength);
            return;
        }

        LOG.debug("Payload size >= partSize ({} > {}). Uploading payload using multi-part operation", contentLength, partSize);
        objectMetadata.put("Content-Length", "" + contentLength);

        final String keyName = AWS2S3Utils.determineKey(exchange, getConfiguration());
        final String bucketName = AWS2S3Utils.determineBucketName(exchange, getConfiguration());
        CreateMultipartUploadRequest.Builder createMultipartUploadRequest
                = CreateMultipartUploadRequest.builder().bucket(getConfiguration().getBucketName()).key(keyName);

        String storageClass = AWS2S3Utils.determineStorageClass(exchange, getConfiguration());
        if (storageClass != null) {
            createMultipartUploadRequest.storageClass(storageClass);
        }

        String cannedAcl = exchange.getIn().getHeader(AWS2S3Constants.CANNED_ACL, String.class);
        if (cannedAcl != null) {
            ObjectCannedACL objectAcl = ObjectCannedACL.valueOf(cannedAcl);
            createMultipartUploadRequest.acl(objectAcl);
        }

        BucketCannedACL acl = exchange.getIn().getHeader(AWS2S3Constants.ACL, BucketCannedACL.class);
        if (acl != null) {
            // note: if cannedacl and acl are both specified the last one will
            // be used. refer to
            // PutObjectRequest#setAccessControlList for more details
            createMultipartUploadRequest.acl(acl.toString());
        }

        String contentType = exchange.getIn().getHeader(AWS2S3Constants.CONTENT_TYPE, String.class);
        if (contentType != null) {
            createMultipartUploadRequest.contentType(contentType);
        }

        String cacheControl = exchange.getIn().getHeader(AWS2S3Constants.CACHE_CONTROL, String.class);
        if (cacheControl != null) {
            createMultipartUploadRequest.cacheControl(cacheControl);
        }

        String contentDisposition = exchange.getIn().getHeader(AWS2S3Constants.CONTENT_DISPOSITION, String.class);
        if (contentDisposition != null) {
            createMultipartUploadRequest.contentDisposition(contentDisposition);
        }

        String contentEncoding = exchange.getIn().getHeader(AWS2S3Constants.CONTENT_ENCODING, String.class);
        if (contentEncoding != null) {
            createMultipartUploadRequest.contentEncoding(contentEncoding);
        }

        AWS2S3Utils.setEncryption(createMultipartUploadRequest, getConfiguration());

        LOG.trace("Initiating multipart upload [{}] from exchange [{}]...", createMultipartUploadRequest, exchange);

        CreateMultipartUploadResponse initResponse
                = getEndpoint().getS3Client().createMultipartUpload(createMultipartUploadRequest.build());
        List<CompletedPart> completedParts = new ArrayList<CompletedPart>();
        CompleteMultipartUploadResponse uploadResult;

        long position = 0;
        try {
            for (int part = 1; position < contentLength; part++) {
                partSize = Math.min(partSize, contentLength - position);

                UploadPartRequest uploadRequest = UploadPartRequest.builder().bucket(getConfiguration().getBucketName())
                        .key(keyName).uploadId(initResponse.uploadId())
                        .partNumber(part).build();

                LOG.debug("Uploading multi-part [{}] at position: [{}] for {}", part, position, keyName);

                String etag = getEndpoint().getS3Client()
                        .uploadPart(uploadRequest, RequestBody.fromInputStream(inputStream, partSize)).eTag();
                CompletedPart partUpload = CompletedPart.builder().partNumber(part).eTag(etag).build();
                completedParts.add(partUpload);
                position += partSize;
            }

            LOG.debug("Completing multi-part upload for {}", keyName);
            CompletedMultipartUpload completeMultipartUpload = CompletedMultipartUpload.builder().parts(completedParts).build();
            CompleteMultipartUploadRequest.Builder compRequestBuilder = CompleteMultipartUploadRequest.builder()
                    .multipartUpload(completeMultipartUpload).bucket(getConfiguration().getBucketName()).key(keyName)
                    .uploadId(initResponse.uploadId());
            if (getConfiguration().isConditionalWritesEnabled()) {
                compRequestBuilder.ifNoneMatch("*");
            }
            uploadResult = getEndpoint().getS3Client().completeMultipartUpload(compRequestBuilder.build());

        } catch (Exception e) {
            getEndpoint().getS3Client()
                    .abortMultipartUpload(AbortMultipartUploadRequest.builder().bucket(getConfiguration().getBucketName())
                            .key(keyName).uploadId(initResponse.uploadId()).build());
            throw e;
        } finally {
            IOHelper.close(inputStream);
        }

        Message message = getMessageForResponse(exchange);
        message.setHeader(AWS2S3Constants.E_TAG, uploadResult.eTag());
        message.setHeader(AWS2S3Constants.PRODUCED_KEY, keyName);
        message.setHeader(AWS2S3Constants.PRODUCED_BUCKET_NAME, bucketName);
        if (uploadResult.versionId() != null) {
            message.setHeader(AWS2S3Constants.VERSION_ID, uploadResult.versionId());
        }

        if (filePayload != null && getConfiguration().isDeleteAfterWrite()) {
            FileUtil.deleteFile(filePayload);
        }
    }

    public void processSingleOp(final Exchange exchange) throws Exception {
        // the content-length may already be known
        long contentLength = exchange.getIn().getHeader(AWS2S3Constants.CONTENT_LENGTH, -1, Long.class);

        Object obj = exchange.getIn().getMandatoryBody();
        InputStream inputStream = null;
        File filePayload = null;

        try {
            // Need to check if the message body is WrappedFile
            if (obj instanceof WrappedFile) {
                WrappedFile<?> wrappedFile = ((WrappedFile<?>) obj);
                contentLength = wrappedFile.getFileLength();
                obj = wrappedFile.getFile();
            }
            if (obj instanceof File) {
                // optimize for file payload
                filePayload = (File) obj;
                contentLength = filePayload.length();
            } else {
                // okay we use input stream
                inputStream = exchange.getIn().getMandatoryBody(InputStream.class);
                if (contentLength <= 0) {
                    contentLength = AWS2S3Utils.determineLengthInputStream(inputStream);
                    if (contentLength == -1) {
                        // fallback to read into memory to calculate length
                        LOG.debug(
                                "The content length is not defined. It needs to be determined by reading the data into memory");
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        IOHelper.copyAndCloseInput(inputStream, baos);
                        byte[] arr = baos.toByteArray();
                        contentLength = arr.length;
                        inputStream = new ByteArrayInputStream(arr);
                    }
                }
            }

            Map<String, String> objectMetadata = determineMetadata(exchange);
            doPutObject(exchange, objectMetadata, filePayload, inputStream, contentLength);
        } finally {
            IOHelper.close(inputStream);
        }

        if (getConfiguration().isDeleteAfterWrite() && filePayload != null) {
            FileUtil.deleteFile(filePayload);
        }
    }

    private void doPutObject(
            Exchange exchange, Map<String, String> objectMetadata,
            File file, InputStream inputStream, long contentLength) {
        final String bucketName = AWS2S3Utils.determineBucketName(exchange, getConfiguration());
        final String keyName = AWS2S3Utils.determineKey(exchange, getConfiguration());

        PutObjectRequest.Builder putObjectRequest = PutObjectRequest.builder();
        putObjectRequest.bucket(bucketName).key(keyName).metadata(objectMetadata);

        String storageClass = AWS2S3Utils.determineStorageClass(exchange, getConfiguration());
        if (storageClass != null) {
            putObjectRequest.storageClass(storageClass);
        }

        String cannedAcl = exchange.getIn().getHeader(AWS2S3Constants.CANNED_ACL, String.class);
        if (cannedAcl != null) {
            ObjectCannedACL objectAcl = ObjectCannedACL.valueOf(cannedAcl);
            putObjectRequest.acl(objectAcl);
        }

        String contentType = exchange.getIn().getHeader(AWS2S3Constants.CONTENT_TYPE, String.class);
        if (contentType != null) {
            putObjectRequest.contentType(contentType);
        }

        String cacheControl = exchange.getIn().getHeader(AWS2S3Constants.CACHE_CONTROL, String.class);
        if (cacheControl != null) {
            putObjectRequest.cacheControl(cacheControl);
        }

        String contentDisposition = exchange.getIn().getHeader(AWS2S3Constants.CONTENT_DISPOSITION, String.class);
        if (contentDisposition != null) {
            putObjectRequest.contentDisposition(contentDisposition);
        }

        String contentEncoding = exchange.getIn().getHeader(AWS2S3Constants.CONTENT_ENCODING, String.class);
        if (contentEncoding != null) {
            putObjectRequest.contentEncoding(contentEncoding);
        }

        if (contentLength > 0) {
            putObjectRequest.contentLength(contentLength);
        }

        BucketCannedACL acl = exchange.getIn().getHeader(AWS2S3Constants.ACL, BucketCannedACL.class);
        if (acl != null) {
            // note: if cannedacl and acl are both specified the last one will
            // be used. refer to
            // PutObjectRequest#setAccessControlList for more details
            putObjectRequest.acl(acl.toString());
        }

        String contentMd5 = exchange.getIn().getHeader(AWS2S3Constants.CONTENT_MD5, String.class);
        if (contentMd5 != null) {
            putObjectRequest.contentMD5(contentMd5);
        }

        if (getConfiguration().isUseAwsKMS()) {
            if (ObjectHelper.isNotEmpty(getConfiguration().getAwsKMSKeyId())) {
                putObjectRequest.ssekmsKeyId(getConfiguration().getAwsKMSKeyId());
                putObjectRequest.serverSideEncryption(ServerSideEncryption.AWS_KMS);
            }
        }

        if (getConfiguration().isUseSSES3()) {
            putObjectRequest.serverSideEncryption(ServerSideEncryption.AES256);
        }

        if (getConfiguration().isUseCustomerKey()) {
            if (ObjectHelper.isNotEmpty(getConfiguration().getCustomerKeyId())) {
                putObjectRequest.sseCustomerKey(getConfiguration().getCustomerKeyId());
            }
            if (ObjectHelper.isNotEmpty(getConfiguration().getCustomerKeyMD5())) {
                putObjectRequest.sseCustomerKeyMD5(getConfiguration().getCustomerKeyMD5());
            }
            if (ObjectHelper.isNotEmpty(getConfiguration().getCustomerAlgorithm())) {
                putObjectRequest.sseCustomerAlgorithm(getConfiguration().getCustomerAlgorithm());
            }
        }

        if (getConfiguration().isConditionalWritesEnabled()) {
            putObjectRequest.ifNoneMatch("*");
        }

        LOG.trace("Put object [{}] from exchange [{}]...", putObjectRequest, exchange);

        RequestBody rb;
        if (file != null) {
            rb = RequestBody.fromFile(file);
        } else {
            rb = RequestBody.fromInputStream(inputStream, contentLength);
        }

        PutObjectResponse putObjectResult = getEndpoint().getS3Client().putObject(putObjectRequest.build(), rb);

        LOG.trace("Received result [{}]", putObjectResult);

        Message message = getMessageForResponse(exchange);
        message.setHeader(AWS2S3Constants.E_TAG, putObjectResult.eTag());
        message.setHeader(AWS2S3Constants.PRODUCED_KEY, keyName);
        message.setHeader(AWS2S3Constants.PRODUCED_BUCKET_NAME, bucketName);
        if (putObjectResult.versionId() != null) {
            message.setHeader(AWS2S3Constants.VERSION_ID, putObjectResult.versionId());
        }
    }

    private void copyObject(S3Client s3Client, Exchange exchange) throws InvalidPayloadException {
        final String bucketName = AWS2S3Utils.determineBucketName(exchange, getConfiguration());
        final String keyName = AWS2S3Utils.determineKey(exchange, getConfiguration());
        final String destinationKey = exchange.getIn().getHeader(AWS2S3Constants.DESTINATION_KEY, String.class);
        final String bucketNameDestination = exchange.getIn().getHeader(AWS2S3Constants.BUCKET_DESTINATION_NAME, String.class);
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof CopyObjectRequest) {
                CopyObjectResponse result;
                result = s3Client.copyObject((CopyObjectRequest) payload);
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            if (ObjectHelper.isEmpty(bucketNameDestination)) {
                throw new IllegalArgumentException("Bucket Name Destination must be specified for copyObject Operation");
            }
            if (ObjectHelper.isEmpty(destinationKey)) {
                throw new IllegalArgumentException("Destination Key must be specified for copyObject Operation");
            }
            CopyObjectRequest.Builder copyObjectRequest = CopyObjectRequest.builder().destinationBucket(bucketNameDestination)
                    .destinationKey(destinationKey).sourceBucket(bucketName).sourceKey(keyName);

            if (getConfiguration().isUseAwsKMS()) {
                if (ObjectHelper.isNotEmpty(getConfiguration().getAwsKMSKeyId())) {
                    copyObjectRequest.ssekmsKeyId(getConfiguration().getAwsKMSKeyId());
                    copyObjectRequest.serverSideEncryption(ServerSideEncryption.AWS_KMS);
                }
            }

            if (getConfiguration().isUseSSES3()) {
                copyObjectRequest.serverSideEncryption(ServerSideEncryption.AES256);
            }

            if (getConfiguration().isUseCustomerKey()) {
                if (ObjectHelper.isNotEmpty(getConfiguration().getCustomerKeyId())) {
                    copyObjectRequest.sseCustomerKey(getConfiguration().getCustomerKeyId());
                }
                if (ObjectHelper.isNotEmpty(getConfiguration().getCustomerKeyMD5())) {
                    copyObjectRequest.sseCustomerKeyMD5(getConfiguration().getCustomerKeyMD5());
                }
                if (ObjectHelper.isNotEmpty(getConfiguration().getCustomerAlgorithm())) {
                    copyObjectRequest.sseCustomerAlgorithm(getConfiguration().getCustomerAlgorithm());
                }
            }
            final String ifMatchCondition = exchange.getMessage().getHeader(AWS2S3Constants.IF_MATCH_CONDITION, String.class);
            final Instant ifModifiedSinceCondition
                    = exchange.getMessage().getHeader(AWS2S3Constants.IF_MODIFIED_SINCE_CONDITION, Instant.class);
            final String ifNoneMatchCondition
                    = exchange.getMessage().getHeader(AWS2S3Constants.IF_NONE_MATCH_CONDITION, String.class);
            final Instant ifUnmodifiedSince
                    = exchange.getMessage().getHeader(AWS2S3Constants.IF_UNMODIFIED_SINCE_CONDITION, Instant.class);
            if (ObjectHelper.isNotEmpty(ifMatchCondition)) {
                copyObjectRequest.copySourceIfMatch(ifMatchCondition);
            }
            if (ObjectHelper.isNotEmpty(ifModifiedSinceCondition)) {
                copyObjectRequest.copySourceIfModifiedSince(ifModifiedSinceCondition);
            }
            if (ObjectHelper.isNotEmpty(ifNoneMatchCondition)) {
                copyObjectRequest.copySourceIfNoneMatch(ifNoneMatchCondition);
            }
            if (ObjectHelper.isNotEmpty(ifUnmodifiedSince)) {
                copyObjectRequest.copySourceIfUnmodifiedSince(ifUnmodifiedSince);
            }

            CopyObjectResponse copyObjectResult = s3Client.copyObject(copyObjectRequest.build());

            Message message = getMessageForResponse(exchange);
            if (copyObjectResult.versionId() != null) {
                message.setHeader(AWS2S3Constants.VERSION_ID, copyObjectResult.versionId());
            }
            message.setHeader(AWS2S3Constants.PRODUCED_KEY, keyName);
            message.setHeader(AWS2S3Constants.PRODUCED_BUCKET_NAME, bucketName);
        }
    }

    private void deleteObject(S3Client s3Client, Exchange exchange) throws InvalidPayloadException {
        final String bucketName = AWS2S3Utils.determineBucketName(exchange, getConfiguration());
        final String keyName = AWS2S3Utils.determineKey(exchange, getConfiguration());

        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof DeleteObjectRequest) {
                s3Client.deleteObject((DeleteObjectRequest) payload);
                Message message = getMessageForResponse(exchange);
                message.setBody(true);
            }
        } else {
            DeleteObjectRequest.Builder deleteObjectRequest = DeleteObjectRequest.builder().bucket(bucketName).key(keyName);
            s3Client.deleteObject(deleteObjectRequest.build());

            Message message = getMessageForResponse(exchange);
            message.setBody(true);
            message.setHeader(AWS2S3Constants.PRODUCED_KEY, keyName);
            message.setHeader(AWS2S3Constants.PRODUCED_BUCKET_NAME, bucketName);
        }
    }

    private void listBuckets(S3Client s3Client, Exchange exchange) {
        ListBucketsResponse bucketsList = s3Client.listBuckets();

        Message message = getMessageForResponse(exchange);
        message.setBody(bucketsList.buckets());
    }

    private void deleteBucket(S3Client s3Client, Exchange exchange) throws InvalidPayloadException {
        final String bucketName = AWS2S3Utils.determineBucketName(exchange, getConfiguration());

        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof DeleteBucketRequest) {
                DeleteBucketResponse resp = s3Client.deleteBucket((DeleteBucketRequest) payload);
                Message message = getMessageForResponse(exchange);
                message.setBody(resp);
            }
        } else {
            DeleteBucketRequest.Builder deleteBucketRequest = DeleteBucketRequest.builder().bucket(bucketName);
            DeleteBucketResponse resp = s3Client.deleteBucket(deleteBucketRequest.build());

            Message message = getMessageForResponse(exchange);
            message.setBody(resp);
        }
    }

    private void getObject(S3Client s3Client, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof GetObjectRequest) {
                ResponseInputStream<GetObjectResponse> res
                        = s3Client.getObject((GetObjectRequest) payload, ResponseTransformer.toInputStream());
                Message message = getMessageForResponse(exchange);
                if (!getConfiguration().isIgnoreBody()) {
                    message.setBody(res);
                }
                populateMetadata(res, message);
            }
        } else {
            final String bucketName = AWS2S3Utils.determineBucketName(exchange, getConfiguration());
            final String keyName = AWS2S3Utils.determineKey(exchange, getConfiguration());
            final String ifMatchCondition = exchange.getMessage().getHeader(AWS2S3Constants.IF_MATCH_CONDITION, String.class);
            final Instant ifModifiedSinceCondition
                    = exchange.getMessage().getHeader(AWS2S3Constants.IF_MODIFIED_SINCE_CONDITION, Instant.class);
            final String ifNoneMatchCondition
                    = exchange.getMessage().getHeader(AWS2S3Constants.IF_NONE_MATCH_CONDITION, String.class);
            final Instant ifUnmodifiedSince
                    = exchange.getMessage().getHeader(AWS2S3Constants.IF_UNMODIFIED_SINCE_CONDITION, Instant.class);
            GetObjectRequest.Builder req = GetObjectRequest.builder().bucket(bucketName).key(keyName);
            if (ObjectHelper.isNotEmpty(ifMatchCondition)) {
                req.ifMatch(ifMatchCondition);
            }
            if (ObjectHelper.isNotEmpty(ifModifiedSinceCondition)) {
                req.ifModifiedSince(ifModifiedSinceCondition);
            }
            if (ObjectHelper.isNotEmpty(ifNoneMatchCondition)) {
                req.ifNoneMatch(ifNoneMatchCondition);
            }
            if (ObjectHelper.isNotEmpty(ifUnmodifiedSince)) {
                req.ifUnmodifiedSince(ifUnmodifiedSince);
            }
            ResponseInputStream<GetObjectResponse> res = s3Client.getObject(req.build(), ResponseTransformer.toInputStream());

            Message message = getMessageForResponse(exchange);
            if (!getConfiguration().isIgnoreBody()) {
                message.setBody(res);
            }
            populateMetadata(res, message);
            message.setHeader(AWS2S3Constants.PRODUCED_KEY, keyName);
            message.setHeader(AWS2S3Constants.PRODUCED_BUCKET_NAME, bucketName);
        }
    }

    private void getObjectRange(S3Client s3Client, Exchange exchange) throws InvalidPayloadException {
        final String bucketName = AWS2S3Utils.determineBucketName(exchange, getConfiguration());
        final String keyName = AWS2S3Utils.determineKey(exchange, getConfiguration());
        final String rangeStart = exchange.getIn().getHeader(AWS2S3Constants.RANGE_START, String.class);
        final String rangeEnd = exchange.getIn().getHeader(AWS2S3Constants.RANGE_END, String.class);

        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof GetObjectRequest) {
                ResponseInputStream<GetObjectResponse> res
                        = s3Client.getObject((GetObjectRequest) payload, ResponseTransformer.toInputStream());
                Message message = getMessageForResponse(exchange);
                message.setBody(res);
            }
        } else {
            if (ObjectHelper.isEmpty(rangeStart) || ObjectHelper.isEmpty(rangeEnd)) {
                throw new IllegalArgumentException(
                        "A Range start and range end header must be configured to perform a range get operation.");
            }

            GetObjectRequest.Builder req = GetObjectRequest.builder().bucket(bucketName).key(keyName)
                    .range("bytes=" + Long.parseLong(rangeStart) + "-" + Long.parseLong(rangeEnd));
            ResponseInputStream<GetObjectResponse> res = s3Client.getObject(req.build(), ResponseTransformer.toInputStream());

            Message message = getMessageForResponse(exchange);
            message.setBody(res);
            message.setHeader(AWS2S3Constants.PRODUCED_KEY, keyName);
            message.setHeader(AWS2S3Constants.PRODUCED_BUCKET_NAME, bucketName);
        }
    }

    private void listObjects(S3Client s3Client, Exchange exchange) throws InvalidPayloadException {
        final String bucketName = AWS2S3Utils.determineBucketName(exchange, getConfiguration());

        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof ListObjectsRequest) {
                ListObjectsResponse objectList = s3Client.listObjects((ListObjectsRequest) payload);
                Message message = getMessageForResponse(exchange);
                message.setBody(objectList.contents());
            }
        } else {
            final String delimiter
                    = exchange.getIn().getHeader(AWS2S3Constants.DELIMITER, getConfiguration().getDelimiter(), String.class);
            final String prefix
                    = exchange.getIn().getHeader(AWS2S3Constants.PREFIX, getConfiguration().getPrefix(), String.class);

            final ListObjectsRequest listObjectsRequest = ListObjectsRequest
                    .builder()
                    .bucket(bucketName)
                    .delimiter(delimiter)
                    .prefix(prefix)
                    .build();
            ListObjectsResponse objectList = s3Client.listObjects(listObjectsRequest);

            Message message = getMessageForResponse(exchange);
            message.setBody(objectList.contents());
        }
    }

    private void createDownloadLink(Exchange exchange) {
        final String bucketName = AWS2S3Utils.determineBucketName(exchange, getConfiguration());
        final String keyName = AWS2S3Utils.determineKey(exchange, getConfiguration());

        long milliSeconds = 0;

        Long expirationMillis = exchange.getIn().getHeader(AWS2S3Constants.DOWNLOAD_LINK_EXPIRATION_TIME, Long.class);
        if (expirationMillis != null) {
            milliSeconds += expirationMillis;
        } else {
            milliSeconds += 1000 * 60 * 60;
        }
        S3Presigner presigner;

        if (ObjectHelper.isNotEmpty(getConfiguration().getAmazonS3Presigner())) {
            presigner = getConfiguration().getAmazonS3Presigner();
        } else {
            S3Presigner.Builder builder = S3Presigner.builder();
            builder.credentialsProvider(
                    getConfiguration().isUseDefaultCredentialsProvider()
                            ? DefaultCredentialsProvider.create() : StaticCredentialsProvider.create(
                                    AwsBasicCredentials.create(getConfiguration().getAccessKey(),
                                            getConfiguration().getSecretKey())))
                    .region(Region.of(getConfiguration().getRegion()));

            String uriEndpointOverride = getConfiguration().getUriEndpointOverride();
            if (ObjectHelper.isNotEmpty(uriEndpointOverride)) {
                builder.endpointOverride(URI.create(uriEndpointOverride));
            }

            presigner = builder.build();
        }

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(keyName)
                .build();

        GetObjectPresignRequest getObjectPresignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMillis(milliSeconds))
                .getObjectRequest(getObjectRequest)
                .build();

        PresignedGetObjectRequest presignedGetObjectRequest = presigner.presignGetObject(getObjectPresignRequest);

        Message message = getMessageForResponse(exchange);
        message.setBody(presignedGetObjectRequest.url().toString());
        message.setHeader(AWS2S3Constants.DOWNLOAD_LINK_BROWSER_COMPATIBLE, presignedGetObjectRequest.isBrowserExecutable());
        message.setHeader(AWS2S3Constants.PRODUCED_KEY, keyName);
        message.setHeader(AWS2S3Constants.PRODUCED_BUCKET_NAME, bucketName);

        if (!presignedGetObjectRequest.isBrowserExecutable()) {
            LOG.debug(
                    "The download link url is not browser compatible and please check the option of checksum validations in Amazon S3 client");
            message.setHeader(AWS2S3Constants.DOWNLOAD_LINK_HTTP_REQUEST_HEADERS,
                    presignedGetObjectRequest.httpRequest().headers());
            presignedGetObjectRequest.signedPayload().ifPresent(payload -> {
                message.setHeader(AWS2S3Constants.DOWNLOAD_LINK_SIGNED_PAYLOAD, payload.asUtf8String());
            });
        }

        if (ObjectHelper.isEmpty(getConfiguration().getAmazonS3Presigner())) {
            presigner.close();
        }
    }

    private void headBucket(S3Client s3Client, Exchange exchange) {
        String bucketName = exchange.getIn().getHeader(AWS2S3Constants.OVERRIDE_BUCKET_NAME, String.class);
        if (ObjectHelper.isEmpty(bucketName)) {
            throw new IllegalArgumentException(
                    "Head Bucket operation requires to specify a bucket name via Header");
        }
        Message message = getMessageForResponse(exchange);
        boolean exists = true;
        try {
            HeadBucketResponse headBucketResponse = s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
            if (!getConfiguration().isIgnoreBody()) {
                message.setBody(headBucketResponse);
            }
        } catch (NoSuchBucketException e) {
            exists = false;
        }
        message.setHeader(AWS2S3Constants.BUCKET_EXISTS, exists);
    }

    private void headObject(S3Client s3Client, Exchange exchange) {
        String key = exchange.getIn().getHeader(AWS2S3Constants.KEY, String.class);
        if (ObjectHelper.isEmpty(key)) {
            throw new IllegalArgumentException(
                    "Head Object operation requires to specify a bucket name via Header");
        }
        HeadObjectResponse headObjectResponse = s3Client.headObject(HeadObjectRequest.builder()
                .bucket(AWS2S3Utils.determineBucketName(exchange, getConfiguration())).key(key).build());

        Message message = getMessageForResponse(exchange);
        message.setBody(headObjectResponse);
    }

    private void deleteObjects(S3Client s3Client, Exchange exchange) throws InvalidPayloadException {
        final String bucketName = AWS2S3Utils.determineBucketName(exchange, getConfiguration());

        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof DeleteObjectsRequest deleteObjectsRequest) {
                DeleteObjectsResponse result = s3Client.deleteObjects(deleteObjectsRequest);
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            List<String> keysToDelete = exchange.getIn().getHeader(AWS2S3Constants.KEYS_TO_DELETE, List.class);
            if (ObjectHelper.isEmpty(keysToDelete)) {
                throw new IllegalArgumentException("Keys to delete must be specified for deleteObjects Operation");
            }

            List<ObjectIdentifier> objectIdentifiers = new ArrayList<>();
            for (String key : keysToDelete) {
                objectIdentifiers.add(ObjectIdentifier.builder().key(key).build());
            }

            Delete delete = Delete.builder().objects(objectIdentifiers).build();
            DeleteObjectsRequest deleteObjectsRequest = DeleteObjectsRequest.builder()
                    .bucket(bucketName)
                    .delete(delete)
                    .build();

            DeleteObjectsResponse result = s3Client.deleteObjects(deleteObjectsRequest);

            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void restoreObject(S3Client s3Client, Exchange exchange) throws InvalidPayloadException {
        final String bucketName = AWS2S3Utils.determineBucketName(exchange, getConfiguration());
        final String keyName = AWS2S3Utils.determineKey(exchange, getConfiguration());

        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof RestoreObjectRequest restoreObjectRequest) {
                RestoreObjectResponse result = s3Client.restoreObject(restoreObjectRequest);
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            Integer days = exchange.getIn().getHeader(AWS2S3Constants.RESTORE_DAYS, 1, Integer.class);
            String tier = exchange.getIn().getHeader(AWS2S3Constants.RESTORE_TIER, "Standard", String.class);

            GlacierJobParameters glacierJobParameters = GlacierJobParameters.builder()
                    .tier(Tier.fromValue(tier))
                    .build();

            RestoreRequest restoreRequest = RestoreRequest.builder()
                    .days(days)
                    .glacierJobParameters(glacierJobParameters)
                    .build();

            RestoreObjectRequest.Builder requestBuilder = RestoreObjectRequest.builder()
                    .bucket(bucketName)
                    .key(keyName)
                    .restoreRequest(restoreRequest);

            RestoreObjectResponse result = s3Client.restoreObject(requestBuilder.build());

            Message message = getMessageForResponse(exchange);
            message.setBody(result);
            message.setHeader(AWS2S3Constants.PRODUCED_KEY, keyName);
            message.setHeader(AWS2S3Constants.PRODUCED_BUCKET_NAME, bucketName);
        }
    }

    private void getObjectTagging(S3Client s3Client, Exchange exchange) throws InvalidPayloadException {
        final String bucketName = AWS2S3Utils.determineBucketName(exchange, getConfiguration());
        final String keyName = AWS2S3Utils.determineKey(exchange, getConfiguration());

        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof GetObjectTaggingRequest getObjectTaggingRequest) {
                GetObjectTaggingResponse result = s3Client.getObjectTagging(getObjectTaggingRequest);
                Message message = getMessageForResponse(exchange);
                message.setBody(result.tagSet());
            }
        } else {
            GetObjectTaggingRequest request = GetObjectTaggingRequest.builder()
                    .bucket(bucketName)
                    .key(keyName)
                    .build();

            GetObjectTaggingResponse result = s3Client.getObjectTagging(request);

            Message message = getMessageForResponse(exchange);
            message.setBody(result.tagSet());
            message.setHeader(AWS2S3Constants.PRODUCED_KEY, keyName);
            message.setHeader(AWS2S3Constants.PRODUCED_BUCKET_NAME, bucketName);
        }
    }

    private void putObjectTagging(S3Client s3Client, Exchange exchange) throws InvalidPayloadException {
        final String bucketName = AWS2S3Utils.determineBucketName(exchange, getConfiguration());
        final String keyName = AWS2S3Utils.determineKey(exchange, getConfiguration());

        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof PutObjectTaggingRequest putObjectTaggingRequest) {
                PutObjectTaggingResponse result = s3Client.putObjectTagging(putObjectTaggingRequest);
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            Map<String, String> tags = exchange.getIn().getHeader(AWS2S3Constants.OBJECT_TAGS, Map.class);
            if (ObjectHelper.isEmpty(tags)) {
                throw new IllegalArgumentException("Object tags must be specified for putObjectTagging Operation");
            }

            List<Tag> tagSet = new ArrayList<>();
            for (Map.Entry<String, String> entry : tags.entrySet()) {
                tagSet.add(Tag.builder().key(entry.getKey()).value(entry.getValue()).build());
            }

            Tagging tagging = Tagging.builder().tagSet(tagSet).build();
            PutObjectTaggingRequest request = PutObjectTaggingRequest.builder()
                    .bucket(bucketName)
                    .key(keyName)
                    .tagging(tagging)
                    .build();

            PutObjectTaggingResponse result = s3Client.putObjectTagging(request);

            Message message = getMessageForResponse(exchange);
            message.setBody(result);
            message.setHeader(AWS2S3Constants.PRODUCED_KEY, keyName);
            message.setHeader(AWS2S3Constants.PRODUCED_BUCKET_NAME, bucketName);
        }
    }

    private void deleteObjectTagging(S3Client s3Client, Exchange exchange) throws InvalidPayloadException {
        final String bucketName = AWS2S3Utils.determineBucketName(exchange, getConfiguration());
        final String keyName = AWS2S3Utils.determineKey(exchange, getConfiguration());

        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof DeleteObjectTaggingRequest deleteObjectTaggingRequest) {
                DeleteObjectTaggingResponse result = s3Client.deleteObjectTagging(deleteObjectTaggingRequest);
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            DeleteObjectTaggingRequest request = DeleteObjectTaggingRequest.builder()
                    .bucket(bucketName)
                    .key(keyName)
                    .build();

            DeleteObjectTaggingResponse result = s3Client.deleteObjectTagging(request);

            Message message = getMessageForResponse(exchange);
            message.setBody(result);
            message.setHeader(AWS2S3Constants.PRODUCED_KEY, keyName);
            message.setHeader(AWS2S3Constants.PRODUCED_BUCKET_NAME, bucketName);
        }
    }

    private void getObjectAcl(S3Client s3Client, Exchange exchange) throws InvalidPayloadException {
        final String bucketName = AWS2S3Utils.determineBucketName(exchange, getConfiguration());
        final String keyName = AWS2S3Utils.determineKey(exchange, getConfiguration());

        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof GetObjectAclRequest getObjectAclRequest) {
                GetObjectAclResponse result = s3Client.getObjectAcl(getObjectAclRequest);
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            GetObjectAclRequest request = GetObjectAclRequest.builder()
                    .bucket(bucketName)
                    .key(keyName)
                    .build();

            GetObjectAclResponse result = s3Client.getObjectAcl(request);

            Message message = getMessageForResponse(exchange);
            message.setBody(result);
            message.setHeader(AWS2S3Constants.PRODUCED_KEY, keyName);
            message.setHeader(AWS2S3Constants.PRODUCED_BUCKET_NAME, bucketName);
        }
    }

    private void putObjectAcl(S3Client s3Client, Exchange exchange) throws InvalidPayloadException {
        final String bucketName = AWS2S3Utils.determineBucketName(exchange, getConfiguration());
        final String keyName = AWS2S3Utils.determineKey(exchange, getConfiguration());

        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof PutObjectAclRequest putObjectAclRequest) {
                PutObjectAclResponse result = s3Client.putObjectAcl(putObjectAclRequest);
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            String cannedAcl = exchange.getIn().getHeader(AWS2S3Constants.CANNED_ACL, String.class);
            if (ObjectHelper.isEmpty(cannedAcl)) {
                throw new IllegalArgumentException("Canned ACL must be specified for putObjectAcl Operation");
            }

            ObjectCannedACL objectCannedACL = ObjectCannedACL.valueOf(cannedAcl);
            PutObjectAclRequest request = PutObjectAclRequest.builder()
                    .bucket(bucketName)
                    .key(keyName)
                    .acl(objectCannedACL)
                    .build();

            PutObjectAclResponse result = s3Client.putObjectAcl(request);

            Message message = getMessageForResponse(exchange);
            message.setBody(result);
            message.setHeader(AWS2S3Constants.PRODUCED_KEY, keyName);
            message.setHeader(AWS2S3Constants.PRODUCED_BUCKET_NAME, bucketName);
        }
    }

    private void createUploadLink(Exchange exchange) {
        final String bucketName = AWS2S3Utils.determineBucketName(exchange, getConfiguration());
        final String keyName = AWS2S3Utils.determineKey(exchange, getConfiguration());

        long milliSeconds = 0;

        Long expirationMillis = exchange.getIn().getHeader(AWS2S3Constants.UPLOAD_LINK_EXPIRATION_TIME, Long.class);
        if (expirationMillis != null) {
            milliSeconds += expirationMillis;
        } else {
            milliSeconds += 1000 * 60 * 60;
        }
        S3Presigner presigner;

        if (ObjectHelper.isNotEmpty(getConfiguration().getAmazonS3Presigner())) {
            presigner = getConfiguration().getAmazonS3Presigner();
        } else {
            S3Presigner.Builder builder = S3Presigner.builder();
            builder.credentialsProvider(
                    getConfiguration().isUseDefaultCredentialsProvider()
                            ? DefaultCredentialsProvider.create() : StaticCredentialsProvider.create(
                                    AwsBasicCredentials.create(getConfiguration().getAccessKey(),
                                            getConfiguration().getSecretKey())))
                    .region(Region.of(getConfiguration().getRegion()));

            String uriEndpointOverride = getConfiguration().getUriEndpointOverride();
            if (ObjectHelper.isNotEmpty(uriEndpointOverride)) {
                builder.endpointOverride(URI.create(uriEndpointOverride));
            }

            presigner = builder.build();
        }

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(keyName)
                .build();

        software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest putObjectPresignRequest
                = software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofMillis(milliSeconds))
                        .putObjectRequest(putObjectRequest)
                        .build();

        software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest presignedPutObjectRequest
                = presigner.presignPutObject(putObjectPresignRequest);

        Message message = getMessageForResponse(exchange);
        message.setBody(presignedPutObjectRequest.url().toString());
        message.setHeader(AWS2S3Constants.UPLOAD_LINK_BROWSER_COMPATIBLE, presignedPutObjectRequest.isBrowserExecutable());
        message.setHeader(AWS2S3Constants.PRODUCED_KEY, keyName);
        message.setHeader(AWS2S3Constants.PRODUCED_BUCKET_NAME, bucketName);

        if (!presignedPutObjectRequest.isBrowserExecutable()) {
            LOG.debug(
                    "The upload link url is not browser compatible and please check the option of checksum validations in Amazon S3 client");
            message.setHeader(AWS2S3Constants.UPLOAD_LINK_HTTP_REQUEST_HEADERS,
                    presignedPutObjectRequest.httpRequest().headers());
            presignedPutObjectRequest.signedPayload().ifPresent(payload -> {
                message.setHeader(AWS2S3Constants.UPLOAD_LINK_SIGNED_PAYLOAD, payload.asUtf8String());
            });
        }

        if (ObjectHelper.isEmpty(getConfiguration().getAmazonS3Presigner())) {
            presigner.close();
        }
    }

    private void createBucket(S3Client s3Client, Exchange exchange) throws InvalidPayloadException {
        final String bucketName = AWS2S3Utils.determineBucketName(exchange, getConfiguration());

        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof CreateBucketRequest) {
                CreateBucketResponse result = s3Client.createBucket((CreateBucketRequest) payload);
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            CreateBucketRequest.Builder requestBuilder = CreateBucketRequest.builder().bucket(bucketName);

            // Add location constraint if region is not us-east-1
            String region = getConfiguration().getRegion();
            if (region != null && !region.equals("us-east-1")) {
                CreateBucketConfiguration bucketConfiguration = CreateBucketConfiguration.builder()
                        .locationConstraint(BucketLocationConstraint.fromValue(region))
                        .build();
                requestBuilder.createBucketConfiguration(bucketConfiguration);
            }

            CreateBucketResponse result = s3Client.createBucket(requestBuilder.build());

            Message message = getMessageForResponse(exchange);
            message.setBody(result);
            message.setHeader(AWS2S3Constants.PRODUCED_BUCKET_NAME, bucketName);
        }
    }

    private void getBucketTagging(S3Client s3Client, Exchange exchange) throws InvalidPayloadException {
        final String bucketName = AWS2S3Utils.determineBucketName(exchange, getConfiguration());

        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof GetBucketTaggingRequest) {
                GetBucketTaggingResponse result = s3Client.getBucketTagging((GetBucketTaggingRequest) payload);
                Message message = getMessageForResponse(exchange);
                message.setBody(result.tagSet());
            }
        } else {
            GetBucketTaggingRequest request = GetBucketTaggingRequest.builder()
                    .bucket(bucketName)
                    .build();

            GetBucketTaggingResponse result = s3Client.getBucketTagging(request);

            Message message = getMessageForResponse(exchange);
            message.setBody(result.tagSet());
            message.setHeader(AWS2S3Constants.PRODUCED_BUCKET_NAME, bucketName);
        }
    }

    private void putBucketTagging(S3Client s3Client, Exchange exchange) throws InvalidPayloadException {
        final String bucketName = AWS2S3Utils.determineBucketName(exchange, getConfiguration());

        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof PutBucketTaggingRequest) {
                PutBucketTaggingResponse result = s3Client.putBucketTagging((PutBucketTaggingRequest) payload);
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            Map<String, String> tags = exchange.getIn().getHeader(AWS2S3Constants.BUCKET_TAGS, Map.class);
            if (ObjectHelper.isEmpty(tags)) {
                throw new IllegalArgumentException("Bucket tags must be specified for putBucketTagging Operation");
            }

            List<Tag> tagSet = new ArrayList<>();
            for (Map.Entry<String, String> entry : tags.entrySet()) {
                tagSet.add(Tag.builder().key(entry.getKey()).value(entry.getValue()).build());
            }

            Tagging tagging = Tagging.builder().tagSet(tagSet).build();
            PutBucketTaggingRequest request = PutBucketTaggingRequest.builder()
                    .bucket(bucketName)
                    .tagging(tagging)
                    .build();

            PutBucketTaggingResponse result = s3Client.putBucketTagging(request);

            Message message = getMessageForResponse(exchange);
            message.setBody(result);
            message.setHeader(AWS2S3Constants.PRODUCED_BUCKET_NAME, bucketName);
        }
    }

    private void deleteBucketTagging(S3Client s3Client, Exchange exchange) throws InvalidPayloadException {
        final String bucketName = AWS2S3Utils.determineBucketName(exchange, getConfiguration());

        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof DeleteBucketTaggingRequest) {
                DeleteBucketTaggingResponse result = s3Client.deleteBucketTagging((DeleteBucketTaggingRequest) payload);
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            DeleteBucketTaggingRequest request = DeleteBucketTaggingRequest.builder()
                    .bucket(bucketName)
                    .build();

            DeleteBucketTaggingResponse result = s3Client.deleteBucketTagging(request);

            Message message = getMessageForResponse(exchange);
            message.setBody(result);
            message.setHeader(AWS2S3Constants.PRODUCED_BUCKET_NAME, bucketName);
        }
    }

    private void getBucketVersioning(S3Client s3Client, Exchange exchange) throws InvalidPayloadException {
        final String bucketName = AWS2S3Utils.determineBucketName(exchange, getConfiguration());

        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof GetBucketVersioningRequest) {
                GetBucketVersioningResponse result = s3Client.getBucketVersioning((GetBucketVersioningRequest) payload);
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            GetBucketVersioningRequest request = GetBucketVersioningRequest.builder()
                    .bucket(bucketName)
                    .build();

            GetBucketVersioningResponse result = s3Client.getBucketVersioning(request);

            Message message = getMessageForResponse(exchange);
            message.setBody(result);
            message.setHeader(AWS2S3Constants.PRODUCED_BUCKET_NAME, bucketName);
        }
    }

    private void putBucketVersioning(S3Client s3Client, Exchange exchange) throws InvalidPayloadException {
        final String bucketName = AWS2S3Utils.determineBucketName(exchange, getConfiguration());

        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof PutBucketVersioningRequest) {
                PutBucketVersioningResponse result = s3Client.putBucketVersioning((PutBucketVersioningRequest) payload);
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            String versioningStatus = exchange.getIn().getHeader(AWS2S3Constants.VERSIONING_STATUS, String.class);
            if (ObjectHelper.isEmpty(versioningStatus)) {
                throw new IllegalArgumentException("Versioning status must be specified for putBucketVersioning Operation");
            }

            VersioningConfiguration.Builder versioningConfigBuilder = VersioningConfiguration.builder()
                    .status(BucketVersioningStatus.fromValue(versioningStatus));

            String mfaDelete = exchange.getIn().getHeader(AWS2S3Constants.MFA_DELETE, String.class);
            if (ObjectHelper.isNotEmpty(mfaDelete)) {
                versioningConfigBuilder.mfaDelete(MFADelete.fromValue(mfaDelete));
            }

            PutBucketVersioningRequest request = PutBucketVersioningRequest.builder()
                    .bucket(bucketName)
                    .versioningConfiguration(versioningConfigBuilder.build())
                    .build();

            PutBucketVersioningResponse result = s3Client.putBucketVersioning(request);

            Message message = getMessageForResponse(exchange);
            message.setBody(result);
            message.setHeader(AWS2S3Constants.PRODUCED_BUCKET_NAME, bucketName);
        }
    }

    private void getBucketPolicy(S3Client s3Client, Exchange exchange) throws InvalidPayloadException {
        final String bucketName = AWS2S3Utils.determineBucketName(exchange, getConfiguration());

        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof GetBucketPolicyRequest) {
                GetBucketPolicyResponse result = s3Client.getBucketPolicy((GetBucketPolicyRequest) payload);
                Message message = getMessageForResponse(exchange);
                message.setBody(result.policy());
            }
        } else {
            GetBucketPolicyRequest request = GetBucketPolicyRequest.builder()
                    .bucket(bucketName)
                    .build();

            GetBucketPolicyResponse result = s3Client.getBucketPolicy(request);

            Message message = getMessageForResponse(exchange);
            message.setBody(result.policy());
            message.setHeader(AWS2S3Constants.PRODUCED_BUCKET_NAME, bucketName);
        }
    }

    private void putBucketPolicy(S3Client s3Client, Exchange exchange) throws InvalidPayloadException {
        final String bucketName = AWS2S3Utils.determineBucketName(exchange, getConfiguration());

        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof PutBucketPolicyRequest) {
                PutBucketPolicyResponse result = s3Client.putBucketPolicy((PutBucketPolicyRequest) payload);
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            String policy = exchange.getIn().getHeader(AWS2S3Constants.BUCKET_POLICY, String.class);
            if (ObjectHelper.isEmpty(policy)) {
                throw new IllegalArgumentException("Bucket policy must be specified for putBucketPolicy Operation");
            }

            PutBucketPolicyRequest request = PutBucketPolicyRequest.builder()
                    .bucket(bucketName)
                    .policy(policy)
                    .build();

            PutBucketPolicyResponse result = s3Client.putBucketPolicy(request);

            Message message = getMessageForResponse(exchange);
            message.setBody(result);
            message.setHeader(AWS2S3Constants.PRODUCED_BUCKET_NAME, bucketName);
        }
    }

    private void deleteBucketPolicy(S3Client s3Client, Exchange exchange) throws InvalidPayloadException {
        final String bucketName = AWS2S3Utils.determineBucketName(exchange, getConfiguration());

        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof DeleteBucketPolicyRequest) {
                DeleteBucketPolicyResponse result = s3Client.deleteBucketPolicy((DeleteBucketPolicyRequest) payload);
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            DeleteBucketPolicyRequest request = DeleteBucketPolicyRequest.builder()
                    .bucket(bucketName)
                    .build();

            DeleteBucketPolicyResponse result = s3Client.deleteBucketPolicy(request);

            Message message = getMessageForResponse(exchange);
            message.setBody(result);
            message.setHeader(AWS2S3Constants.PRODUCED_BUCKET_NAME, bucketName);
        }
    }

    private AWS2S3Operations determineOperation(Exchange exchange) {
        AWS2S3Operations operation = exchange.getIn().getHeader(AWS2S3Constants.S3_OPERATION, AWS2S3Operations.class);
        if (operation == null) {
            operation = getConfiguration().getOperation();
        }
        return operation;
    }

    private Map<String, String> determineMetadata(final Exchange exchange) {
        Map<String, String> objectMetadata = new HashMap<>();

        Map<String, String> metadata = exchange.getIn().getHeader(AWS2S3Constants.METADATA, Map.class);
        if (metadata != null) {
            objectMetadata.putAll(metadata);
        }

        return objectMetadata;
    }

    private static void populateMetadata(ResponseInputStream<GetObjectResponse> res, Message message) {
        message.setHeader(AWS2S3Constants.E_TAG, res.response().eTag());
        message.setHeader(AWS2S3Constants.VERSION_ID, res.response().versionId());
        message.setHeader(AWS2S3Constants.CONTENT_TYPE, res.response().contentType());
        message.setHeader(AWS2S3Constants.CONTENT_LENGTH, res.response().contentLength());
        message.setHeader(AWS2S3Constants.CONTENT_ENCODING, res.response().contentEncoding());
        message.setHeader(AWS2S3Constants.CONTENT_DISPOSITION, res.response().contentDisposition());
        message.setHeader(AWS2S3Constants.CACHE_CONTROL, res.response().cacheControl());
        message.setHeader(AWS2S3Constants.SERVER_SIDE_ENCRYPTION, res.response().serverSideEncryption());
        message.setHeader(AWS2S3Constants.EXPIRATION_TIME, res.response().expiration());
        message.setHeader(AWS2S3Constants.REPLICATION_STATUS, res.response().replicationStatus());
        message.setHeader(AWS2S3Constants.STORAGE_CLASS, res.response().storageClass());
        message.setHeader(AWS2S3Constants.METADATA, res.response().metadata());
    }

    protected AWS2S3Configuration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    @Override
    public AWS2S3Endpoint getEndpoint() {
        return (AWS2S3Endpoint) super.getEndpoint();
    }

    public static Message getMessageForResponse(final Exchange exchange) {
        return exchange.getMessage();
    }

}
