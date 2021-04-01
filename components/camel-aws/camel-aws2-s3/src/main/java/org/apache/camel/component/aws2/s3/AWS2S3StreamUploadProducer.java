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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.utils.IoUtils;

/**
 * A Producer which sends messages to the Amazon Web Service Simple Storage Service
 * <a href="http://aws.amazon.com/s3/">AWS S3</a>
 */
public class AWS2S3StreamUploadProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(AWS2S3StreamUploadProducer.class);

    private transient String s3ProducerToString;

    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    CreateMultipartUploadResponse initResponse;
    int index = 1;
    List<CompletedPart> completedParts = new ArrayList<CompletedPart>();
    int part = 0;

    public AWS2S3StreamUploadProducer(final Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(final Exchange exchange) throws Exception {
        streamUpload(exchange);
    }

    public void streamUpload(final Exchange exchange) throws Exception {
        File filePayload = null;
        InputStream is = exchange.getIn().getMandatoryBody(InputStream.class);

        buffer.write(IoUtils.toByteArray(is));
        final String keyName = determineKey(exchange);
        String dynamicKeyName;
        if (part > 0) {
            dynamicKeyName = keyName + "-" + part;
        } else {
            dynamicKeyName = keyName;
        }
        CreateMultipartUploadRequest.Builder createMultipartUploadRequest
                = CreateMultipartUploadRequest.builder().bucket(getConfiguration().getBucketName()).key(dynamicKeyName);

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
            createMultipartUploadRequest.serverSideEncryption(ServerSideEncryption.AWS_KMS);
        }

        if (getConfiguration().isUseCustomerKey()) {
            if (ObjectHelper.isNotEmpty(getConfiguration().getCustomerKeyId())) {
                createMultipartUploadRequest.sseCustomerKey(getConfiguration().getCustomerKeyId());
            }
            if (ObjectHelper.isNotEmpty(getConfiguration().getCustomerKeyMD5())) {
                createMultipartUploadRequest.sseCustomerKeyMD5(getConfiguration().getCustomerKeyMD5());
            }
            if (ObjectHelper.isNotEmpty(getConfiguration().getCustomerAlgorithm())) {
                createMultipartUploadRequest.sseCustomerAlgorithm(getConfiguration().getCustomerAlgorithm());
            }
        }

        LOG.trace("Initiating multipart upload [{}] from exchange [{}]...", createMultipartUploadRequest, exchange);
        CompleteMultipartUploadResponse uploadResult = null;
        if (index == 1) {
            initResponse
                    = getEndpoint().getS3Client().createMultipartUpload(createMultipartUploadRequest.build());
            //final long contentLength = Long.valueOf(objectMetadata.get("Content-Length"));
            completedParts = new ArrayList<CompletedPart>();
            long partSize = getConfiguration().getPartSize();
        }

        long filePosition = 0;

        try {
            if (buffer.size() >= 5000000 || index == 100) {

                UploadPartRequest uploadRequest = UploadPartRequest.builder().bucket(getConfiguration().getBucketName())
                        .key(dynamicKeyName).uploadId(initResponse.uploadId())
                        .partNumber(index).build();

                LOG.trace("Uploading part [{}] for {}", index, keyName);

                String etag = getEndpoint().getS3Client()
                        .uploadPart(uploadRequest, RequestBody.fromBytes(buffer.toByteArray())).eTag();
                CompletedPart partUpload = CompletedPart.builder().partNumber(index).eTag(etag).build();
                completedParts.add(partUpload);
                buffer.reset();
                part++;
            }

            if (index == 100) {
                CompletedMultipartUpload completeMultipartUpload
                        = CompletedMultipartUpload.builder().parts(completedParts).build();
                CompleteMultipartUploadRequest compRequest
                        = CompleteMultipartUploadRequest.builder().multipartUpload(completeMultipartUpload)
                                .bucket(getConfiguration().getBucketName()).key(dynamicKeyName)
                                .uploadId(initResponse.uploadId())
                                .build();

                uploadResult = getEndpoint().getS3Client().completeMultipartUpload(compRequest);
                index = 0;
            }

        } catch (Exception e) {
            getEndpoint().getS3Client()
                    .abortMultipartUpload(AbortMultipartUploadRequest.builder().bucket(getConfiguration().getBucketName())
                            .key(dynamicKeyName).uploadId(initResponse.uploadId()).build());
            throw e;
        }

        index++;

        Message message = getMessageForResponse(exchange);

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
     * Reads the bucket name from the header of the given exchange. If not provided, it's read from the endpoint
     * configuration.
     *
     * @param  exchange                 The exchange to read the header from.
     * @return                          The bucket name.
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
        return (AWS2S3Endpoint) super.getEndpoint();
    }

    public static Message getMessageForResponse(final Exchange exchange) {
        return exchange.getMessage();
    }

}
