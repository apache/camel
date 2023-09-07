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
package org.apache.camel.component.aws2.s3.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.camel.Exchange;
import org.apache.camel.StreamCache;
import org.apache.camel.component.aws2.s3.AWS2S3Configuration;
import org.apache.camel.component.aws2.s3.AWS2S3Constants;
import org.apache.camel.util.ObjectHelper;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

public final class AWS2S3Utils {

    private AWS2S3Utils() {
    }

    /**
     * Reads the bucket name from the header of the given exchange. If not provided, it's read from the endpoint
     * configuration.
     *
     * @param  exchange                 The exchange to read the header from
     * @param  configuration            The AWS2 S3 configuration
     * @return                          The bucket name.
     * @throws IllegalArgumentException if the header could not be determined.
     */
    public static String determineBucketName(final Exchange exchange, AWS2S3Configuration configuration) {
        String bucketName = exchange.getIn().getHeader(AWS2S3Constants.BUCKET_NAME, String.class);

        if (ObjectHelper.isEmpty(bucketName)) {
            bucketName = configuration.getBucketName();
        }

        if (bucketName == null) {
            throw new IllegalArgumentException("AWS S3 Bucket name header is missing or not configured.");
        }

        return bucketName;
    }

    public static String determineStorageClass(final Exchange exchange, AWS2S3Configuration configuration) {
        String storageClass = exchange.getIn().getHeader(AWS2S3Constants.STORAGE_CLASS, String.class);
        if (storageClass == null) {
            storageClass = configuration.getStorageClass();
        }

        return storageClass;
    }

    public static String determineFileExtension(String keyName) {
        int extPosition = keyName.lastIndexOf(".");
        if (extPosition == -1) {
            return "";
        } else {
            return keyName.substring(extPosition);
        }
    }

    public static String determineFileName(String keyName) {
        int extPosition = keyName.lastIndexOf(".");
        if (extPosition == -1) {
            return keyName;
        } else {
            return keyName.substring(0, extPosition);
        }
    }

    public static long determineLengthInputStream(InputStream is) throws IOException {
        if (is instanceof StreamCache) {
            long len = ((StreamCache) is).length();
            if (len > 0) {
                return len;
            }
        }

        if (!is.markSupported()) {
            return -1;
        }
        if (is instanceof ByteArrayInputStream) {
            return is.available();
        }
        long size = 0;
        try {
            is.mark(1024);
            int i = is.available();
            while (i > 0) {
                long skip = is.skip(i);
                size += skip;
                i = is.available();
            }
        } finally {
            is.reset();
        }
        return size;
    }

    public static byte[] toByteArray(InputStream is, final int size) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] data = new byte[4096];
            int total = 0;
            int n = 0;
            while (total < size && (n = is.read(data)) != -1) {
                output.write(data, 0, n);
                total += n;
            }
            return output.toByteArray();
        }
    }

    public static String determineKey(final Exchange exchange, AWS2S3Configuration configuration) {
        String key = exchange.getIn().getHeader(AWS2S3Constants.KEY, String.class);
        if (ObjectHelper.isEmpty(key)) {
            key = configuration.getKeyName();
        }
        if (key == null) {
            throw new IllegalArgumentException("AWS S3 Key header missing.");
        }
        return key;
    }

    public static void setEncryption(
            CreateMultipartUploadRequest.Builder createMultipartUploadRequest, AWS2S3Configuration configuration) {
        if (configuration.isUseAwsKMS()) {
            createMultipartUploadRequest.ssekmsKeyId(configuration.getAwsKMSKeyId());
            createMultipartUploadRequest.serverSideEncryption(ServerSideEncryption.AWS_KMS);
        }

        if (configuration.isUseSSES3()) {
            createMultipartUploadRequest.serverSideEncryption(ServerSideEncryption.AES256);
        }

        if (configuration.isUseCustomerKey()) {
            if (ObjectHelper.isNotEmpty(configuration.getCustomerKeyId())) {
                createMultipartUploadRequest.sseCustomerKey(configuration.getCustomerKeyId());
            }
            if (ObjectHelper.isNotEmpty(configuration.getCustomerKeyMD5())) {
                createMultipartUploadRequest.sseCustomerKeyMD5(configuration.getCustomerKeyMD5());
            }
            if (ObjectHelper.isNotEmpty(configuration.getCustomerAlgorithm())) {
                createMultipartUploadRequest.sseCustomerAlgorithm(configuration.getCustomerAlgorithm());
            }
        }
    }
}
