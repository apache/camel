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
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.WrappedFile;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
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
import software.amazon.awssdk.services.s3.model.UploadPartRequest;

/**
 * A Producer which sends messages to the Amazon Web Service Simple Storage
 * Service <a href="http://aws.amazon.com/s3/">AWS S3</a>
 */
public class AWS2S3Producer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(AWS2S3Producer.class);

    private transient String s3ProducerToString;

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
            obj = ((WrappedFile<?>)obj).getFile();
        }
        if (obj instanceof File) {
            filePayload = (File)obj;
        } else {
            throw new IllegalArgumentException("aws-s3: MultiPart upload requires a File input.");
        }

        Map<String, String> objectMetadata = determineMetadata(exchange);
        if (objectMetadata.containsKey("Content-Length")) {
            if (objectMetadata.get("Content-Length").equalsIgnoreCase("0")) {
                objectMetadata.put("Content-Length", String.valueOf(filePayload.length()));
            }
        } else {
            objectMetadata.put("Content-Length", String.valueOf(filePayload.length()));
        }

        final String keyName = determineKey(exchange);
        CreateMultipartUploadRequest.Builder createMultipartUploadRequest = CreateMultipartUploadRequest.builder().bucket(getConfiguration().getBucketName()).key(keyName);

        String storageClass = determineStorageClass(exchange);
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

        if (getConfiguration().isUseAwsKMS()) {
            createMultipartUploadRequest.ssekmsKeyId(getConfiguration().getAwsKMSKeyId());
        }

        LOG.trace("Initiating multipart upload [{}] from exchange [{}]...", createMultipartUploadRequest, exchange);

        CreateMultipartUploadResponse initResponse = getEndpoint().getS3Client().createMultipartUpload(createMultipartUploadRequest.build());
        final long contentLength = Long.valueOf(objectMetadata.get("Content-Length"));
        List<CompletedPart> completedParts = new ArrayList<CompletedPart>();
        long partSize = getConfiguration().getPartSize();
        CompleteMultipartUploadResponse uploadResult = null;

        long filePosition = 0;

        try {
            for (int part = 1; filePosition < contentLength; part++) {
                partSize = Math.min(partSize, contentLength - filePosition);

                UploadPartRequest uploadRequest = UploadPartRequest.builder().bucket(getConfiguration().getBucketName()).key(keyName).uploadId(initResponse.uploadId())
                    .partNumber(part).build();

                LOG.trace("Uploading part [{}] for {}", part, keyName);
                String etag = getEndpoint().getS3Client().uploadPart(uploadRequest, RequestBody.fromFile(filePayload)).eTag();
                CompletedPart partUpload = CompletedPart.builder().partNumber(part).eTag(etag).build();
                completedParts.add(partUpload);
                filePosition += partSize;
            }
            CompletedMultipartUpload completeMultipartUpload = CompletedMultipartUpload.builder().parts(completedParts).build();
            CompleteMultipartUploadRequest compRequest = CompleteMultipartUploadRequest.builder().multipartUpload(completeMultipartUpload)
                .bucket(getConfiguration().getBucketName()).key(keyName).uploadId(initResponse.uploadId()).build();

            uploadResult = getEndpoint().getS3Client().completeMultipartUpload(compRequest);

        } catch (Exception e) {
            getEndpoint().getS3Client()
                .abortMultipartUpload(AbortMultipartUploadRequest.builder().bucket(getConfiguration().getBucketName()).key(keyName).uploadId(initResponse.uploadId()).build());
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

        Map<String, String> objectMetadata = determineMetadata(exchange);

        File filePayload = null;
        InputStream is = null;
        ByteArrayOutputStream baos = null;
        Object obj = exchange.getIn().getMandatoryBody();
        PutObjectRequest.Builder putObjectRequest = PutObjectRequest.builder();
        // Need to check if the message body is WrappedFile
        if (obj instanceof WrappedFile) {
            obj = ((WrappedFile<?>)obj).getFile();
        }
        if (obj instanceof File) {
            filePayload = (File)obj;
            is = new FileInputStream(filePayload);
        } else {
            is = exchange.getIn().getMandatoryBody(InputStream.class);
            if (objectMetadata.containsKey(Exchange.CONTENT_LENGTH)) {
                if (objectMetadata.get("Content-Length").equals("0") && ObjectHelper.isEmpty(exchange.getProperty(Exchange.CONTENT_LENGTH))) {
                    LOG.debug("The content length is not defined. It needs to be determined by reading the data into memory");
                    baos = determineLengthInputStream(is);
                    objectMetadata.put("Content-Length", String.valueOf(baos.size()));
                    is = new ByteArrayInputStream(baos.toByteArray());
                } else {
                    if (ObjectHelper.isNotEmpty(exchange.getProperty(Exchange.CONTENT_LENGTH))) {
                        objectMetadata.put("Content-Length", exchange.getProperty(Exchange.CONTENT_LENGTH, String.class));
                    }
                }
            }
        }

        final String bucketName = determineBucketName(exchange);
        final String key = determineKey(exchange);
        putObjectRequest.bucket(bucketName).key(key).metadata(objectMetadata);

        String storageClass = determineStorageClass(exchange);
        if (storageClass != null) {
            putObjectRequest.storageClass(storageClass);
        }

        String cannedAcl = exchange.getIn().getHeader(AWS2S3Constants.CANNED_ACL, String.class);
        if (cannedAcl != null) {
            ObjectCannedACL objectAcl = ObjectCannedACL.valueOf(cannedAcl);
            putObjectRequest.acl(objectAcl);
        }

        BucketCannedACL acl = exchange.getIn().getHeader(AWS2S3Constants.ACL, BucketCannedACL.class);
        if (acl != null) {
            // note: if cannedacl and acl are both specified the last one will
            // be used. refer to
            // PutObjectRequest#setAccessControlList for more details
            putObjectRequest.acl(acl.toString());
        }

        if (getConfiguration().isUseAwsKMS()) {
            if (ObjectHelper.isNotEmpty(getConfiguration().getAwsKMSKeyId())) {
                putObjectRequest.ssekmsKeyId(getConfiguration().getAwsKMSKeyId());
            }
        }

        LOG.trace("Put object [{}] from exchange [{}]...", putObjectRequest, exchange);

        PutObjectResponse putObjectResult = getEndpoint().getS3Client().putObject(putObjectRequest.build(), RequestBody.fromBytes(SdkBytes.fromInputStream(is).asByteArray()));

        LOG.trace("Received result [{}]", putObjectResult);

        Message message = getMessageForResponse(exchange);
        message.setHeader(AWS2S3Constants.E_TAG, putObjectResult.eTag());
        if (putObjectResult.versionId() != null) {
            message.setHeader(AWS2S3Constants.VERSION_ID, putObjectResult.versionId());
        }

        IOHelper.close(is);

        if (getConfiguration().isDeleteAfterWrite() && filePayload != null) {
            FileUtil.deleteFile(filePayload);
        }
    }

    private void copyObject(S3Client s3Client, Exchange exchange) {
        final String bucketName = determineBucketName(exchange);
        final String sourceKey = determineKey(exchange);
        final String destinationKey = exchange.getIn().getHeader(AWS2S3Constants.DESTINATION_KEY, String.class);
        final String bucketNameDestination = exchange.getIn().getHeader(AWS2S3Constants.BUCKET_DESTINATION_NAME, String.class);

        if (ObjectHelper.isEmpty(bucketNameDestination)) {
            throw new IllegalArgumentException("Bucket Name Destination must be specified for copyObject Operation");
        }
        if (ObjectHelper.isEmpty(destinationKey)) {
            throw new IllegalArgumentException("Destination Key must be specified for copyObject Operation");
        }
        CopyObjectRequest.Builder copyObjectRequest = CopyObjectRequest.builder();
        copyObjectRequest = CopyObjectRequest.builder().destinationBucket(bucketNameDestination).destinationKey(destinationKey).copySource(bucketName + "/" + sourceKey);

        if (getConfiguration().isUseAwsKMS()) {
            if (ObjectHelper.isNotEmpty(getConfiguration().getAwsKMSKeyId())) {
                copyObjectRequest.ssekmsKeyId(getConfiguration().getAwsKMSKeyId());
            }
        }

        CopyObjectResponse copyObjectResult = s3Client.copyObject(copyObjectRequest.build());

        Message message = getMessageForResponse(exchange);
        if (copyObjectResult.versionId() != null) {
            message.setHeader(AWS2S3Constants.VERSION_ID, copyObjectResult.versionId());
        }
    }

    private void deleteObject(S3Client s3Client, Exchange exchange) {
        final String bucketName = determineBucketName(exchange);
        final String sourceKey = determineKey(exchange);

        DeleteObjectRequest.Builder deleteObjectRequest = DeleteObjectRequest.builder().bucket(bucketName).key(sourceKey);
        s3Client.deleteObject(deleteObjectRequest.build());

        Message message = getMessageForResponse(exchange);
        message.setBody(true);
    }

    private void listBuckets(S3Client s3Client, Exchange exchange) {
        ListBucketsResponse bucketsList = s3Client.listBuckets();

        Message message = getMessageForResponse(exchange);
        message.setBody(bucketsList.buckets());
    }

    private void deleteBucket(S3Client s3Client, Exchange exchange) {
        final String bucketName = determineBucketName(exchange);

        DeleteBucketRequest.Builder deleteBucketRequest = DeleteBucketRequest.builder().bucket(bucketName);
        DeleteBucketResponse resp = s3Client.deleteBucket(deleteBucketRequest.build());

        Message message = getMessageForResponse(exchange);
        message.setBody(resp);
    }

    private void getObject(S3Client s3Client, Exchange exchange) {
        final String bucketName = determineBucketName(exchange);
        final String sourceKey = determineKey(exchange);

        GetObjectRequest.Builder req = GetObjectRequest.builder().bucket(bucketName).key(sourceKey);
        ResponseInputStream<GetObjectResponse> res = s3Client.getObject(req.build(), ResponseTransformer.toInputStream());

        Message message = getMessageForResponse(exchange);
        message.setBody(res);
    }

    private void getObjectRange(S3Client s3Client, Exchange exchange) {
        final String bucketName = determineBucketName(exchange);
        final String sourceKey = determineKey(exchange);
        final String rangeStart = exchange.getIn().getHeader(AWS2S3Constants.RANGE_START, String.class);
        final String rangeEnd = exchange.getIn().getHeader(AWS2S3Constants.RANGE_END, String.class);

        if (ObjectHelper.isEmpty(rangeStart) || ObjectHelper.isEmpty(rangeEnd)) {
            throw new IllegalArgumentException("A Range start and range end header must be configured to perform a range get operation.");
        }

        GetObjectRequest.Builder req = GetObjectRequest.builder().bucket(bucketName).key(sourceKey).range("bytes=" + Long.parseLong(rangeStart) + "-" + Long.parseLong(rangeEnd));
        ResponseInputStream<GetObjectResponse> res = s3Client.getObject(req.build(), ResponseTransformer.toInputStream());

        Message message = getMessageForResponse(exchange);
        message.setBody(res);
    }

    private void listObjects(S3Client s3Client, Exchange exchange) {
        final String bucketName = determineBucketName(exchange);

        ListObjectsResponse objectList = s3Client.listObjects(ListObjectsRequest.builder().bucket(bucketName).build());

        Message message = getMessageForResponse(exchange);
        message.setBody(objectList.contents());
    }

    private AWS2S3Operations determineOperation(Exchange exchange) {
        AWS2S3Operations operation = exchange.getIn().getHeader(AWS2S3Constants.S3_OPERATION, AWS2S3Operations.class);
        if (operation == null) {
            operation = getConfiguration().getOperation();
        }
        return operation;
    }

    private Map<String, String> determineMetadata(final Exchange exchange) {
        Map<String, String> objectMetadata = new HashMap<String, String>();

        Long contentLength = exchange.getIn().getHeader(AWS2S3Constants.CONTENT_LENGTH, Long.class);
        if (contentLength != null) {
            objectMetadata.put("Content-Length", String.valueOf(contentLength));
        }

        String contentType = exchange.getIn().getHeader(AWS2S3Constants.CONTENT_TYPE, String.class);
        if (contentType != null) {
            objectMetadata.put("Content-Type", String.valueOf(contentType));
        }

        String cacheControl = exchange.getIn().getHeader(AWS2S3Constants.CACHE_CONTROL, String.class);
        if (cacheControl != null) {
            objectMetadata.put("Cache-Control", String.valueOf(cacheControl));
        }

        String contentDisposition = exchange.getIn().getHeader(AWS2S3Constants.CONTENT_DISPOSITION, String.class);
        if (contentDisposition != null) {
            objectMetadata.put("Content-Disposition", String.valueOf(contentDisposition));
        }

        String contentEncoding = exchange.getIn().getHeader(AWS2S3Constants.CONTENT_ENCODING, String.class);
        if (contentEncoding != null) {
            objectMetadata.put("Content-Encoding", String.valueOf(contentEncoding));
        }

        String contentMD5 = exchange.getIn().getHeader(AWS2S3Constants.CONTENT_MD5, String.class);
        if (contentMD5 != null) {
            objectMetadata.put("Content-Md5", String.valueOf(contentMD5));
        }

        return objectMetadata;
    }

    /**
     * Reads the bucket name from the header of the given exchange. If not
     * provided, it's read from the endpoint configuration.
     *
     * @param exchange The exchange to read the header from.
     * @return The bucket name.
     * @throws IllegalArgumentException if the header could not be determined.
     */
    private String determineBucketName(final Exchange exchange) {
        String bucketName = exchange.getIn().getHeader(AWS2S3Constants.BUCKET_NAME, String.class);

        if (ObjectHelper.isEmpty(bucketName)) {
            bucketName = getConfiguration().getBucketName();
            LOG.trace("AWS S3 Bucket name header is missing, using default one [{}]", bucketName);
        }

        if (bucketName == null) {
            throw new IllegalArgumentException("AWS S3 Bucket name header is missing or not configured.");
        }

        return bucketName;
    }

    private String determineKey(final Exchange exchange) {
        String key = exchange.getIn().getHeader(AWS2S3Constants.KEY, String.class);
        if (ObjectHelper.isEmpty(key)) {
            key = getConfiguration().getKeyName();
        }
        if (key == null) {
            throw new IllegalArgumentException("AWS S3 Key header missing.");
        }
        return key;
    }

    private String determineStorageClass(final Exchange exchange) {
        String storageClass = exchange.getIn().getHeader(AWS2S3Constants.STORAGE_CLASS, String.class);
        if (storageClass == null) {
            storageClass = getConfiguration().getStorageClass();
        }

        return storageClass;
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

    protected AWS2S3Configuration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    @Override
    public String toString() {
        if (s3ProducerToString == null) {
            s3ProducerToString = "S3Producer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
        }
        return s3ProducerToString;
    }

    @Override
    public AWS2S3Endpoint getEndpoint() {
        return (AWS2S3Endpoint)super.getEndpoint();
    }

    public static Message getMessageForResponse(final Exchange exchange) {
        return exchange.getMessage();
    }

}
