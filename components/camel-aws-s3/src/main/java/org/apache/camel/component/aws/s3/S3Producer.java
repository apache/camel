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
package org.apache.camel.component.aws.s3;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.AbortMultipartUploadRequest;
import com.amazonaws.services.s3.model.AccessControlList;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.CompleteMultipartUploadRequest;
import com.amazonaws.services.s3.model.CompleteMultipartUploadResult;
import com.amazonaws.services.s3.model.CopyObjectRequest;
import com.amazonaws.services.s3.model.CopyObjectResult;
import com.amazonaws.services.s3.model.DeleteBucketRequest;
import com.amazonaws.services.s3.model.DeleteObjectRequest;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.InitiateMultipartUploadRequest;
import com.amazonaws.services.s3.model.InitiateMultipartUploadResult;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PartETag;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.SSEAwsKeyManagementParams;
import com.amazonaws.services.s3.model.StorageClass;
import com.amazonaws.services.s3.model.UploadPartRequest;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.WrappedFile;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Producer which sends messages to the Amazon Web Service Simple Storage
 * Service <a href="http://aws.amazon.com/s3/">AWS S3</a>
 */
public class S3Producer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(S3Producer.class);

    private transient String s3ProducerToString;

    public S3Producer(final Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(final Exchange exchange) throws Exception {
        S3Operations operation = determineOperation(exchange);
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
                case downloadLink:
                    createDownloadLink(getEndpoint().getS3Client(), exchange);
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

        ObjectMetadata objectMetadata = determineMetadata(exchange);
        if (objectMetadata.getContentLength() == 0) {
            objectMetadata.setContentLength(filePayload.length());
        }

        final String keyName = determineKey(exchange);
        final InitiateMultipartUploadRequest initRequest = new InitiateMultipartUploadRequest(getConfiguration().getBucketName(), keyName, objectMetadata);

        String storageClass = determineStorageClass(exchange);
        if (storageClass != null) {
            initRequest.setStorageClass(StorageClass.fromValue(storageClass));
        }

        String cannedAcl = exchange.getIn().getHeader(S3Constants.CANNED_ACL, String.class);
        if (cannedAcl != null) {
            CannedAccessControlList objectAcl = CannedAccessControlList.valueOf(cannedAcl);
            initRequest.setCannedACL(objectAcl);
        }

        AccessControlList acl = exchange.getIn().getHeader(S3Constants.ACL, AccessControlList.class);
        if (acl != null) {
            // note: if cannedacl and acl are both specified the last one will
            // be used. refer to
            // PutObjectRequest#setAccessControlList for more details
            initRequest.setAccessControlList(acl);
        }

        if (getConfiguration().isUseAwsKMS()) {
            SSEAwsKeyManagementParams keyManagementParams;
            if (ObjectHelper.isNotEmpty(getConfiguration().getAwsKMSKeyId())) {
                keyManagementParams = new SSEAwsKeyManagementParams(getConfiguration().getAwsKMSKeyId());
            } else {
                keyManagementParams = new SSEAwsKeyManagementParams();
            }
            initRequest.setSSEAwsKeyManagementParams(keyManagementParams);
        }

        LOG.trace("Initiating multipart upload [{}] from exchange [{}]...", initRequest, exchange);

        final InitiateMultipartUploadResult initResponse = getEndpoint().getS3Client().initiateMultipartUpload(initRequest);
        final long contentLength = objectMetadata.getContentLength();
        final List<PartETag> partETags = new ArrayList<>();
        long partSize = getConfiguration().getPartSize();
        CompleteMultipartUploadResult uploadResult = null;

        long filePosition = 0;

        try {
            for (int part = 1; filePosition < contentLength; part++) {
                partSize = Math.min(partSize, contentLength - filePosition);

                UploadPartRequest uploadRequest = new UploadPartRequest().withBucketName(getConfiguration().getBucketName()).withKey(keyName)
                    .withUploadId(initResponse.getUploadId()).withPartNumber(part).withFileOffset(filePosition).withFile(filePayload).withPartSize(partSize);

                LOG.trace("Uploading part [{}] for {}", part, keyName);
                partETags.add(getEndpoint().getS3Client().uploadPart(uploadRequest).getPartETag());

                filePosition += partSize;
            }
            CompleteMultipartUploadRequest compRequest = new CompleteMultipartUploadRequest(getConfiguration().getBucketName(), keyName, initResponse.getUploadId(), partETags);

            uploadResult = getEndpoint().getS3Client().completeMultipartUpload(compRequest);

        } catch (Exception e) {
            getEndpoint().getS3Client().abortMultipartUpload(new AbortMultipartUploadRequest(getConfiguration().getBucketName(), keyName, initResponse.getUploadId()));
            throw e;
        }

        Message message = getMessageForResponse(exchange);
        message.setHeader(S3Constants.E_TAG, uploadResult.getETag());
        if (uploadResult.getVersionId() != null) {
            message.setHeader(S3Constants.VERSION_ID, uploadResult.getVersionId());
        }

        if (getConfiguration().isDeleteAfterWrite()) {
            FileUtil.deleteFile(filePayload);
        }
    }

    public void processSingleOp(final Exchange exchange) throws Exception {

        ObjectMetadata objectMetadata = determineMetadata(exchange);

        File filePayload = null;
        InputStream is = null;
        ByteArrayOutputStream baos = null;
        Object obj = exchange.getIn().getMandatoryBody();
        PutObjectRequest putObjectRequest = null;
        // Need to check if the message body is WrappedFile
        if (obj instanceof WrappedFile) {
            obj = ((WrappedFile<?>)obj).getFile();
        }
        if (obj instanceof File) {
            filePayload = (File)obj;
            is = new FileInputStream(filePayload);
        } else {
            is = exchange.getIn().getMandatoryBody(InputStream.class);
            if (objectMetadata.getContentLength() == 0 && ObjectHelper.isEmpty(exchange.getProperty(Exchange.CONTENT_LENGTH))) {
                LOG.debug("The content length is not defined. It needs to be determined by reading the data into memory");
                baos = determineLengthInputStream(is);
                objectMetadata.setContentLength(baos.size());
                is = new ByteArrayInputStream(baos.toByteArray());
            } else {
                if (ObjectHelper.isNotEmpty(exchange.getProperty(Exchange.CONTENT_LENGTH))) {
                    objectMetadata.setContentLength(Long.valueOf(exchange.getProperty(Exchange.CONTENT_LENGTH, String.class)));
                }
            }
        }

        final String bucketName = determineBucketName(exchange);
        final String key = determineKey(exchange);
        putObjectRequest = new PutObjectRequest(bucketName, key, is, objectMetadata);

        String storageClass = determineStorageClass(exchange);
        if (storageClass != null) {
            putObjectRequest.setStorageClass(storageClass);
        }

        String cannedAcl = exchange.getIn().getHeader(S3Constants.CANNED_ACL, String.class);
        if (cannedAcl != null) {
            CannedAccessControlList objectAcl = CannedAccessControlList.valueOf(cannedAcl);
            putObjectRequest.setCannedAcl(objectAcl);
        }

        AccessControlList acl = exchange.getIn().getHeader(S3Constants.ACL, AccessControlList.class);
        if (acl != null) {
            // note: if cannedacl and acl are both specified the last one will
            // be used. refer to
            // PutObjectRequest#setAccessControlList for more details
            putObjectRequest.setAccessControlList(acl);
        }

        if (getConfiguration().isUseAwsKMS()) {
            SSEAwsKeyManagementParams keyManagementParams;
            if (ObjectHelper.isNotEmpty(getConfiguration().getAwsKMSKeyId())) {
                keyManagementParams = new SSEAwsKeyManagementParams(getConfiguration().getAwsKMSKeyId());
            } else {
                keyManagementParams = new SSEAwsKeyManagementParams();
            }
            putObjectRequest.setSSEAwsKeyManagementParams(keyManagementParams);
        }

        LOG.trace("Put object [{}] from exchange [{}]...", putObjectRequest, exchange);

        PutObjectResult putObjectResult = getEndpoint().getS3Client().putObject(putObjectRequest);

        LOG.trace("Received result [{}]", putObjectResult);

        Message message = getMessageForResponse(exchange);
        message.setHeader(S3Constants.E_TAG, putObjectResult.getETag());
        if (putObjectResult.getVersionId() != null) {
            message.setHeader(S3Constants.VERSION_ID, putObjectResult.getVersionId());
        }

        // close streams
        IOHelper.close(putObjectRequest.getInputStream());
        IOHelper.close(is);

        if (getConfiguration().isDeleteAfterWrite() && filePayload != null) {
            FileUtil.deleteFile(filePayload);
        }
    }

    private void copyObject(AmazonS3 s3Client, Exchange exchange) {
        final String bucketName = determineBucketName(exchange);
        final String sourceKey = determineKey(exchange);
        final String destinationKey = exchange.getIn().getHeader(S3Constants.DESTINATION_KEY, String.class);
        final String bucketNameDestination = exchange.getIn().getHeader(S3Constants.BUCKET_DESTINATION_NAME, String.class);
        final String versionId = exchange.getIn().getHeader(S3Constants.VERSION_ID, String.class);

        if (ObjectHelper.isEmpty(bucketNameDestination)) {
            throw new IllegalArgumentException("Bucket Name Destination must be specified for copyObject Operation");
        }
        if (ObjectHelper.isEmpty(destinationKey)) {
            throw new IllegalArgumentException("Destination Key must be specified for copyObject Operation");
        }
        CopyObjectRequest copyObjectRequest;
        if (ObjectHelper.isEmpty(versionId)) {
            copyObjectRequest = new CopyObjectRequest(bucketName, sourceKey, bucketNameDestination, destinationKey);
        } else {
            copyObjectRequest = new CopyObjectRequest(bucketName, sourceKey, versionId, bucketNameDestination, destinationKey);
        }

        if (getConfiguration().isUseAwsKMS()) {
            SSEAwsKeyManagementParams keyManagementParams;
            if (ObjectHelper.isNotEmpty(getConfiguration().getAwsKMSKeyId())) {
                keyManagementParams = new SSEAwsKeyManagementParams(getConfiguration().getAwsKMSKeyId());
            } else {
                keyManagementParams = new SSEAwsKeyManagementParams();
            }
            copyObjectRequest.setSSEAwsKeyManagementParams(keyManagementParams);
        }

        CopyObjectResult copyObjectResult = s3Client.copyObject(copyObjectRequest);

        Message message = getMessageForResponse(exchange);
        message.setHeader(S3Constants.E_TAG, copyObjectResult.getETag());
        if (copyObjectResult.getVersionId() != null) {
            message.setHeader(S3Constants.VERSION_ID, copyObjectResult.getVersionId());
        }
    }

    private void deleteObject(AmazonS3 s3Client, Exchange exchange) {
        final String bucketName = determineBucketName(exchange);
        final String sourceKey = determineKey(exchange);

        DeleteObjectRequest deleteObjectRequest;
        deleteObjectRequest = new DeleteObjectRequest(bucketName, sourceKey);
        s3Client.deleteObject(deleteObjectRequest);

        Message message = getMessageForResponse(exchange);
        message.setBody(true);
    }

    private void listBuckets(AmazonS3 s3Client, Exchange exchange) {
        List<Bucket> bucketsList = s3Client.listBuckets();

        Message message = getMessageForResponse(exchange);
        message.setBody(bucketsList);
    }

    private void deleteBucket(AmazonS3 s3Client, Exchange exchange) {
        final String bucketName = determineBucketName(exchange);

        DeleteBucketRequest deleteBucketRequest = new DeleteBucketRequest(bucketName);
        s3Client.deleteBucket(deleteBucketRequest);
    }

    private void getObject(AmazonS3 s3Client, Exchange exchange) {
        final String bucketName = determineBucketName(exchange);
        final String sourceKey = determineKey(exchange);

        GetObjectRequest req = new GetObjectRequest(bucketName, sourceKey);
        S3Object res = s3Client.getObject(req);

        Message message = getMessageForResponse(exchange);
        message.setBody(res);
    }

    private void getObjectRange(AmazonS3 s3Client, Exchange exchange) {
        final String bucketName = determineBucketName(exchange);
        final String sourceKey = determineKey(exchange);
        final String rangeStart = exchange.getIn().getHeader(S3Constants.RANGE_START, String.class);
        final String rangeEnd = exchange.getIn().getHeader(S3Constants.RANGE_END, String.class);

        if (ObjectHelper.isEmpty(rangeStart) || ObjectHelper.isEmpty(rangeEnd)) {
            throw new IllegalArgumentException("A Range start and range end header must be configured to perform a range get operation.");
        }

        GetObjectRequest req = new GetObjectRequest(bucketName, sourceKey).withRange(Long.parseLong(rangeStart), Long.parseLong(rangeEnd));
        S3Object res = s3Client.getObject(req);

        Message message = getMessageForResponse(exchange);
        message.setBody(res);
    }

    private void listObjects(AmazonS3 s3Client, Exchange exchange) {
        final String bucketName = determineBucketName(exchange);

        ObjectListing objectList = s3Client.listObjects(bucketName);

        Message message = getMessageForResponse(exchange);
        message.setBody(objectList);
    }

    private S3Operations determineOperation(Exchange exchange) {
        S3Operations operation = exchange.getIn().getHeader(S3Constants.S3_OPERATION, S3Operations.class);
        if (operation == null) {
            operation = getConfiguration().getOperation();
        }
        return operation;
    }

    private ObjectMetadata determineMetadata(final Exchange exchange) {
        ObjectMetadata objectMetadata = new ObjectMetadata();

        Long contentLength = exchange.getIn().getHeader(S3Constants.CONTENT_LENGTH, Long.class);
        if (contentLength != null) {
            objectMetadata.setContentLength(contentLength);
        }

        String contentType = exchange.getIn().getHeader(S3Constants.CONTENT_TYPE, String.class);
        if (contentType != null) {
            objectMetadata.setContentType(contentType);
        }

        String cacheControl = exchange.getIn().getHeader(S3Constants.CACHE_CONTROL, String.class);
        if (cacheControl != null) {
            objectMetadata.setCacheControl(cacheControl);
        }

        String contentDisposition = exchange.getIn().getHeader(S3Constants.CONTENT_DISPOSITION, String.class);
        if (contentDisposition != null) {
            objectMetadata.setContentDisposition(contentDisposition);
        }

        String contentEncoding = exchange.getIn().getHeader(S3Constants.CONTENT_ENCODING, String.class);
        if (contentEncoding != null) {
            objectMetadata.setContentEncoding(contentEncoding);
        }

        String contentMD5 = exchange.getIn().getHeader(S3Constants.CONTENT_MD5, String.class);
        if (contentMD5 != null) {
            objectMetadata.setContentMD5(contentMD5);
        }

        Date lastModified = exchange.getIn().getHeader(S3Constants.LAST_MODIFIED, Date.class);
        if (lastModified != null) {
            objectMetadata.setLastModified(lastModified);
        }

        Map<String, String> userMetadata = CastUtils.cast(exchange.getIn().getHeader(S3Constants.USER_METADATA, Map.class));
        if (userMetadata != null) {
            objectMetadata.setUserMetadata(userMetadata);
        }

        Map<String, String> s3Headers = CastUtils.cast(exchange.getIn().getHeader(S3Constants.S3_HEADERS, Map.class));
        if (s3Headers != null) {
            for (Map.Entry<String, String> entry : s3Headers.entrySet()) {
                objectMetadata.setHeader(entry.getKey(), entry.getValue());
            }
        }

        String encryption = exchange.getIn().getHeader(S3Constants.SERVER_SIDE_ENCRYPTION, getConfiguration().getServerSideEncryption(), String.class);
        if (encryption != null) {
            objectMetadata.setSSEAlgorithm(encryption);
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
        String bucketName = exchange.getIn().getHeader(S3Constants.BUCKET_NAME, String.class);

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
        String key = exchange.getIn().getHeader(S3Constants.KEY, String.class);
        if (ObjectHelper.isEmpty(key)) {
            key = getConfiguration().getKeyName();
        }
        if (key == null) {
            throw new IllegalArgumentException("AWS S3 Key header missing.");
        }
        return key;
    }

    private String determineStorageClass(final Exchange exchange) {
        String storageClass = exchange.getIn().getHeader(S3Constants.STORAGE_CLASS, String.class);
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

    private void createDownloadLink(AmazonS3 s3Client, Exchange exchange) {
        final String bucketName = determineBucketName(exchange);

        String key = exchange.getIn().getHeader(S3Constants.KEY, String.class);
        if (key == null) {
            throw new IllegalArgumentException("AWS S3 Key header is missing.");
        }

        Date expiration = new Date();
        long milliSeconds = expiration.getTime();

        Long expirationMillis = exchange.getIn().getHeader(S3Constants.DOWNLOAD_LINK_EXPIRATION, Long.class);
        if (expirationMillis != null) {
            milliSeconds += expirationMillis;
        } else {
            milliSeconds += 1000 * 60 * 60; // Default: Add 1 hour.
        }

        expiration.setTime(milliSeconds);

        GeneratePresignedUrlRequest generatePresignedUrlRequest = new GeneratePresignedUrlRequest(bucketName, key);
        generatePresignedUrlRequest.setMethod(HttpMethod.GET);
        generatePresignedUrlRequest.setExpiration(expiration);

        URL url = s3Client.generatePresignedUrl(generatePresignedUrlRequest);

        Message message = getMessageForResponse(exchange);
        message.setHeader(S3Constants.DOWNLOAD_LINK, url.toString());
    }

    protected S3Configuration getConfiguration() {
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
    public S3Endpoint getEndpoint() {
        return (S3Endpoint)super.getEndpoint();
    }

    public static Message getMessageForResponse(final Exchange exchange) {
        return exchange.getMessage();
    }

}
