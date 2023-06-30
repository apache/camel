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
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.BucketCannedACL;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.CopyObjectResponse;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.DeleteBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteBucketResponse;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListBucketsResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsResponse;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
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
                default:
                    throw new IllegalArgumentException("Unsupported operation");
            }
        }
    }

    public void processMultiPart(final Exchange exchange) throws Exception {
        File filePayload = null;
        Object obj = exchange.getIn().getMandatoryBody();
        // Need to check if the message body is WrappedFile
        if (obj instanceof WrappedFile) {
            obj = ((WrappedFile<?>) obj).getFile();
        }
        if (obj instanceof File) {
            filePayload = (File) obj;
        } else {
            throw new IllegalArgumentException("aws2-s3: MultiPart upload requires a File input.");
        }

        Map<String, String> objectMetadata = determineMetadata(exchange);

        Long contentLength = exchange.getIn().getHeader(AWS2S3Constants.CONTENT_LENGTH, Long.class);
        if (contentLength == null || contentLength == 0) {
            contentLength = filePayload.length();
        }
        objectMetadata.put("Content-Length", contentLength.toString());

        final String keyName = AWS2S3Utils.determineKey(exchange, getConfiguration());
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
        //final long contentLength = Long.parseLong(objectMetadata.get("Content-Length"));
        List<CompletedPart> completedParts = new ArrayList<CompletedPart>();
        long partSize = getConfiguration().getPartSize();
        CompleteMultipartUploadResponse uploadResult = null;

        long filePosition = 0;

        try {
            for (int part = 1; filePosition < contentLength; part++) {
                partSize = Math.min(partSize, contentLength - filePosition);

                UploadPartRequest uploadRequest = UploadPartRequest.builder().bucket(getConfiguration().getBucketName())
                        .key(keyName).uploadId(initResponse.uploadId())
                        .partNumber(part).build();

                LOG.trace("Uploading part [{}] for {}", part, keyName);
                try (InputStream fileInputStream = new FileInputStream(filePayload)) {
                    if (filePosition > 0) {
                        long skipped = fileInputStream.skip(filePosition);
                        if (skipped == 0) {
                            LOG.warn("While trying to upload the file {} file, 0 bytes were skipped", keyName);
                        }
                    }

                    String etag = getEndpoint().getS3Client()
                            .uploadPart(uploadRequest, RequestBody.fromInputStream(fileInputStream, partSize)).eTag();
                    CompletedPart partUpload = CompletedPart.builder().partNumber(part).eTag(etag).build();
                    completedParts.add(partUpload);
                    filePosition += partSize;
                }
            }
            CompletedMultipartUpload completeMultipartUpload = CompletedMultipartUpload.builder().parts(completedParts).build();
            CompleteMultipartUploadRequest compRequest
                    = CompleteMultipartUploadRequest.builder().multipartUpload(completeMultipartUpload)
                            .bucket(getConfiguration().getBucketName()).key(keyName).uploadId(initResponse.uploadId()).build();

            uploadResult = getEndpoint().getS3Client().completeMultipartUpload(compRequest);

        } catch (Exception e) {
            getEndpoint().getS3Client()
                    .abortMultipartUpload(AbortMultipartUploadRequest.builder().bucket(getConfiguration().getBucketName())
                            .key(keyName).uploadId(initResponse.uploadId()).build());
            throw e;
        }

        Message message = getMessageForResponse(exchange);
        message.setHeader(AWS2S3Constants.E_TAG, uploadResult.eTag());
        if (uploadResult.versionId() != null) {
            message.setHeader(AWS2S3Constants.VERSION_ID, uploadResult.versionId());
        }

        if (getConfiguration().isDeleteAfterWrite()) {
            FileUtil.deleteFile(filePayload);
        }
    }

    public void processSingleOp(final Exchange exchange) throws Exception {
        PutObjectRequest.Builder putObjectRequest = PutObjectRequest.builder();

        Map<String, String> objectMetadata = determineMetadata(exchange);

        // the content-length may already be known
        long contentLength = exchange.getIn().getHeader(AWS2S3Constants.CONTENT_LENGTH, -1, Long.class);

        Object obj = exchange.getIn().getMandatoryBody();
        InputStream inputStream = null;
        File filePayload = null;

        try {
            // Need to check if the message body is WrappedFile
            if (obj instanceof WrappedFile) {
                obj = ((WrappedFile<?>) obj).getFile();
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

            doPutObject(exchange, putObjectRequest, objectMetadata, filePayload, inputStream, contentLength);
        } finally {
            IOHelper.close(inputStream);
        }

        if (getConfiguration().isDeleteAfterWrite() && filePayload != null) {
            FileUtil.deleteFile(filePayload);
        }
    }

    private void doPutObject(
            Exchange exchange, PutObjectRequest.Builder putObjectRequest, Map<String, String> objectMetadata,
            File file, InputStream inputStream, long contentLength) {
        final String bucketName = AWS2S3Utils.determineBucketName(exchange, getConfiguration());
        final String key = AWS2S3Utils.determineKey(exchange, getConfiguration());
        putObjectRequest.bucket(bucketName).key(key).metadata(objectMetadata);

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
        if (putObjectResult.versionId() != null) {
            message.setHeader(AWS2S3Constants.VERSION_ID, putObjectResult.versionId());
        }
    }

    private void copyObject(S3Client s3Client, Exchange exchange) throws InvalidPayloadException {
        final String bucketName = AWS2S3Utils.determineBucketName(exchange, getConfiguration());
        final String sourceKey = AWS2S3Utils.determineKey(exchange, getConfiguration());
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
                    .destinationKey(destinationKey).sourceBucket(bucketName).sourceKey(sourceKey);

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

            CopyObjectResponse copyObjectResult = s3Client.copyObject(copyObjectRequest.build());

            Message message = getMessageForResponse(exchange);
            if (copyObjectResult.versionId() != null) {
                message.setHeader(AWS2S3Constants.VERSION_ID, copyObjectResult.versionId());
            }
        }
    }

    private void deleteObject(S3Client s3Client, Exchange exchange) throws InvalidPayloadException {
        final String bucketName = AWS2S3Utils.determineBucketName(exchange, getConfiguration());
        final String sourceKey = AWS2S3Utils.determineKey(exchange, getConfiguration());

        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof DeleteObjectRequest) {
                s3Client.deleteObject((DeleteObjectRequest) payload);
                Message message = getMessageForResponse(exchange);
                message.setBody(true);
            }
        } else {
            DeleteObjectRequest.Builder deleteObjectRequest = DeleteObjectRequest.builder().bucket(bucketName).key(sourceKey);
            s3Client.deleteObject(deleteObjectRequest.build());

            Message message = getMessageForResponse(exchange);
            message.setBody(true);
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
                message.setBody(res);
                populateMetadata(res, message);
            }
        } else {
            final String bucketName = AWS2S3Utils.determineBucketName(exchange, getConfiguration());
            final String sourceKey = AWS2S3Utils.determineKey(exchange, getConfiguration());
            GetObjectRequest.Builder req = GetObjectRequest.builder().bucket(bucketName).key(sourceKey);
            ResponseInputStream<GetObjectResponse> res = s3Client.getObject(req.build(), ResponseTransformer.toInputStream());

            Message message = getMessageForResponse(exchange);
            message.setBody(res);
            populateMetadata(res, message);
        }
    }

    private void getObjectRange(S3Client s3Client, Exchange exchange) throws InvalidPayloadException {
        final String bucketName = AWS2S3Utils.determineBucketName(exchange, getConfiguration());
        final String sourceKey = AWS2S3Utils.determineKey(exchange, getConfiguration());
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

            GetObjectRequest.Builder req = GetObjectRequest.builder().bucket(bucketName).key(sourceKey)
                    .range("bytes=" + Long.parseLong(rangeStart) + "-" + Long.parseLong(rangeEnd));
            ResponseInputStream<GetObjectResponse> res = s3Client.getObject(req.build(), ResponseTransformer.toInputStream());

            Message message = getMessageForResponse(exchange);
            message.setBody(res);
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
        final String key = AWS2S3Utils.determineKey(exchange, getConfiguration());

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
                .key(key)
                .build();

        GetObjectPresignRequest getObjectPresignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMillis(milliSeconds))
                .getObjectRequest(getObjectRequest)
                .build();

        PresignedGetObjectRequest presignedGetObjectRequest = presigner.presignGetObject(getObjectPresignRequest);

        Message message = getMessageForResponse(exchange);
        message.setBody(presignedGetObjectRequest.url().toString());
        message.setHeader(AWS2S3Constants.DOWNLOAD_LINK_BROWSER_COMPATIBLE, presignedGetObjectRequest.isBrowserExecutable());

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
