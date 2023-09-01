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
package org.apache.camel.component.aws2.s3.stream;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.aws2.s3.AWS2S3Configuration;
import org.apache.camel.component.aws2.s3.AWS2S3Constants;
import org.apache.camel.component.aws2.s3.AWS2S3Endpoint;
import org.apache.camel.component.aws2.s3.utils.AWS2S3Utils;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.BucketCannedACL;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable;

/**
 * A Producer which sends messages to the Amazon Web Service Simple Storage Service
 * <a href="http://aws.amazon.com/s3/">AWS S3</a>
 */
public class AWS2S3StreamUploadProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(AWS2S3StreamUploadProducer.class);
    private static final String TIMEOUT_CHECKER_EXECUTOR_NAME = "S3_Streaming_Upload_Timeout_Checker";
    private AtomicInteger part = new AtomicInteger();
    private UploadState uploadAggregate = null;
    private final Object lock = new Object();
    private transient String s3ProducerToString;
    private ScheduledExecutorService timeoutCheckerExecutorService;

    public AWS2S3StreamUploadProducer(final Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (getConfiguration().getStreamingUploadTimeout() > 0) {
            timeoutCheckerExecutorService
                    = getEndpoint().getCamelContext().getExecutorServiceManager().newSingleThreadScheduledExecutor(this,
                            TIMEOUT_CHECKER_EXECUTOR_NAME);
            timeoutCheckerExecutorService.scheduleAtFixedRate(new StreamingUploadTimeoutTask(),
                    getConfiguration().getStreamingUploadTimeout(), getConfiguration().getStreamingUploadTimeout(),
                    TimeUnit.MILLISECONDS);
        }
        if (getConfiguration().getRestartingPolicy().equals(AWSS3RestartingPolicyEnum.lastPart)) {
            setStartingPart();
        }
    }

    @Override
    protected void doStop() throws Exception {
        synchronized (lock) {
            if (ObjectHelper.isNotEmpty(uploadAggregate)) {
                uploadPart(uploadAggregate);
                completeUpload(uploadAggregate);
            }
        }
        if (timeoutCheckerExecutorService != null) {
            getEndpoint().getCamelContext().getExecutorServiceManager().shutdown(timeoutCheckerExecutorService);
            timeoutCheckerExecutorService = null;
        }
        super.doStop();

    }

    /**
     * Background task that triggers completion based on interval.
     */
    private final class StreamingUploadTimeoutTask implements Runnable {

        @Override
        public void run() {
            synchronized (lock) {
                if (ObjectHelper.isNotEmpty(uploadAggregate)) {
                    uploadPart(uploadAggregate);
                    completeUpload(uploadAggregate);
                    uploadAggregate = null;
                }
            }
        }
    }

    @Override
    public void process(final Exchange exchange) throws Exception {
        InputStream is = exchange.getIn().getMandatoryBody(InputStream.class);

        UploadState state = null;
        int totalSize = 0;
        byte[] b;
        while ((b = AWS2S3Utils.toByteArray(is, getConfiguration().getBufferSize())).length > 0) {
            totalSize += b.length;
            synchronized (lock) {
                // aggregate with previously received exchanges
                if (ObjectHelper.isNotEmpty(uploadAggregate)) {
                    uploadAggregate.buffer.write(b);
                    uploadAggregate.index++;

                    if (uploadAggregate.buffer.size() >= getConfiguration().getBatchSize()
                            || uploadAggregate.index == getConfiguration().getBatchMessageNumber()) {

                        uploadPart(uploadAggregate);
                        CompleteMultipartUploadResponse uploadResult = completeUpload(uploadAggregate);
                        this.uploadAggregate = null;

                        Message message = getMessageForResponse(exchange);
                        message.setHeader(AWS2S3Constants.E_TAG, uploadResult.eTag());
                        if (uploadResult.versionId() != null) {
                            message.setHeader(AWS2S3Constants.VERSION_ID, uploadResult.versionId());
                        }
                    }
                    continue;
                }
            }

            if (state == null) {
                state = new UploadState();
            } else {
                state.index++;
            }
            state.buffer.write(b);

            final String keyName = getConfiguration().getKeyName();
            final String fileName = AWS2S3Utils.determineFileName(keyName);
            final String extension = AWS2S3Utils.determineFileExtension(keyName);
            if (state.index == 1 && getConfiguration().getNamingStrategy().equals(AWSS3NamingStrategyEnum.random)) {
                state.id = UUID.randomUUID();
            }
            state.dynamicKeyName = fileNameToUpload(fileName, getConfiguration().getNamingStrategy(), extension,
                    state.part, state.id);
            CreateMultipartUploadRequest.Builder createMultipartUploadRequest
                    = CreateMultipartUploadRequest.builder().bucket(getConfiguration().getBucketName())
                            .key(state.dynamicKeyName);

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

            AWS2S3Utils.setEncryption(createMultipartUploadRequest, getConfiguration());

            LOG.trace("Initiating multipart upload [{}] from exchange [{}]...", createMultipartUploadRequest, exchange);
            if (state.index == 1) {
                state.initResponse
                        = getEndpoint().getS3Client().createMultipartUpload(createMultipartUploadRequest.build());
            }

            try {
                if (totalSize >= getConfiguration().getBatchSize()
                        || state.buffer.size() >= getConfiguration().getBatchSize()
                        || state.index == getConfiguration().getBatchMessageNumber()) {

                    uploadPart(state);
                    CompleteMultipartUploadResponse uploadResult = completeUpload(state);

                    Message message = getMessageForResponse(exchange);
                    message.setHeader(AWS2S3Constants.E_TAG, uploadResult.eTag());
                    if (uploadResult.versionId() != null) {
                        message.setHeader(AWS2S3Constants.VERSION_ID, uploadResult.versionId());
                    }
                    state = null;
                }

            } catch (Exception e) {
                getEndpoint().getS3Client()
                        .abortMultipartUpload(AbortMultipartUploadRequest.builder().bucket(getConfiguration().getBucketName())
                                .key(state.dynamicKeyName).uploadId(state.initResponse.uploadId()).build());
                throw e;
            }
        }

        if (ObjectHelper.isNotEmpty(state)) {
            // exchange wasn't large enough to send, batch it with subsequent exchanges.
            synchronized (lock) {
                if (this.uploadAggregate == null) {
                    this.uploadAggregate = state;
                }
            }
        }
    }

    private CompleteMultipartUploadResponse completeUpload(UploadState state) {
        CompletedMultipartUpload completeMultipartUpload
                = CompletedMultipartUpload.builder().parts(state.completedParts).build();
        CompleteMultipartUploadRequest compRequest
                = CompleteMultipartUploadRequest.builder().multipartUpload(completeMultipartUpload)
                        .bucket(getConfiguration().getBucketName()).key(state.dynamicKeyName)
                        .uploadId(state.initResponse.uploadId())
                        .build();

        CompleteMultipartUploadResponse uploadResult = getEndpoint().getS3Client().completeMultipartUpload(compRequest);

        // Converting the index to String can cause extra overhead
        if (LOG.isInfoEnabled()) {
            LOG.info("Completed upload for the part {} with etag {} at index {}", part, uploadResult.eTag(),
                    state.index);
        }
        return uploadResult;
    }

    private void uploadPart(UploadState state) {
        UploadPartRequest uploadRequest = UploadPartRequest.builder().bucket(getConfiguration().getBucketName())
                .key(state.dynamicKeyName).uploadId(state.initResponse.uploadId())
                .partNumber(state.index).build();

        LOG.trace("Uploading part {} at index {} for {}", state.part, state.index, getConfiguration().getKeyName());

        String etag = getEndpoint().getS3Client()
                .uploadPart(uploadRequest, RequestBody.fromBytes(state.buffer.toByteArray())).eTag();
        CompletedPart partUpload = CompletedPart.builder().partNumber(state.index).eTag(etag).build();
        state.completedParts.add(partUpload);
        state.buffer.reset();
        part.getAndIncrement();
    }

    private String fileNameToUpload(
            String fileName, AWSS3NamingStrategyEnum strategy, String ext, int part, UUID id) {
        String dynamicKeyName;
        switch (strategy) {
            case progressive:
                if (part > 0) {
                    if (ObjectHelper.isNotEmpty(ext)) {
                        dynamicKeyName = fileName + "-" + part + ext;
                    } else {
                        dynamicKeyName = fileName + "-" + part;
                    }
                } else {
                    if (ObjectHelper.isNotEmpty(ext)) {
                        dynamicKeyName = fileName + ext;
                    } else {
                        dynamicKeyName = fileName;
                    }
                }
                break;
            case random:
                if (part > 0) {
                    if (ObjectHelper.isNotEmpty(ext)) {
                        dynamicKeyName = fileName + "-" + id.toString() + ext;
                    } else {
                        dynamicKeyName = fileName + "-" + id.toString();
                    }
                } else {
                    if (ObjectHelper.isNotEmpty(ext)) {
                        dynamicKeyName = fileName + ext;
                    } else {
                        dynamicKeyName = fileName;
                    }
                }
                break;
            default:
                throw new IllegalArgumentException("Unsupported operation");
        }
        return dynamicKeyName;
    }

    private void setStartingPart() {
        if (getConfiguration().getNamingStrategy().equals(AWSS3NamingStrategyEnum.progressive)) {
            ArrayList<S3Object> list = new ArrayList<>();
            ListObjectsV2Request request = ListObjectsV2Request.builder().bucket(getConfiguration().getBucketName())
                    .prefix(AWS2S3Utils.determineFileName(getConfiguration().getKeyName())).build();
            ListObjectsV2Iterable listRes = getEndpoint().getS3Client().listObjectsV2Paginator(request);
            listRes.stream()
                    .flatMap(r -> r.contents().stream())
                    .forEach(content -> list.add(content));
            if (!list.isEmpty()) {
                list.sort(Comparator.comparing(S3Object::lastModified));
                int listSize = list.size();
                String fileName = AWS2S3Utils.determineFileName(list.get(listSize - 1).key());
                int position = fileName.lastIndexOf("-");
                if (position != -1) {
                    String partString = fileName.substring(position + 1);
                    if (ObjectHelper.isNotEmpty(partString)) {
                        part.getAndSet(Integer.parseInt(partString) + 1);
                    }
                } else {
                    part.getAndSet(1);
                }
            }
        } else {
            LOG.info("lastPart restarting policy can be used only with progressive naming strategy");
        }
    }

    protected AWS2S3Configuration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    @Override
    public String toString() {
        if (s3ProducerToString == null) {
            s3ProducerToString = "AWS2S3StreamUploadProducer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
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

    private class UploadState {
        int index;
        int part;
        List<CompletedPart> completedParts = new ArrayList<>();
        ByteArrayOutputStream buffer;
        String dynamicKeyName;
        UUID id;
        CreateMultipartUploadResponse initResponse;

        UploadState() {
            index = 1;
            part = AWS2S3StreamUploadProducer.this.part.get();
            buffer = new ByteArrayOutputStream();
        }
    }
}
