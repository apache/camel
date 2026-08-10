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
package org.apache.camel.component.alibaba.oss;

import java.io.IOException;
import java.io.InputStream;

import com.aliyun.sdk.service.oss2.models.GetObjectResult;
import com.aliyun.sdk.service.oss2.utils.IOUtils;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.alibaba.oss.constants.OSSConstants;
import org.apache.camel.component.alibaba.oss.constants.OSSHeaders;

public final class OSSUtils {
    private OSSUtils() {
    }

    /**
     * Maps the OSS object along with all its metadata into the exchange
     */
    public static void mapOssObject(Exchange exchange, String bucketName, String objectKey, GetObjectResult result)
            throws IOException {
        Message message = exchange.getIn();

        try (InputStream stream = result.body()) {
            message.setBody(IOUtils.toByteArray(stream));
        }

        message.setHeader(OSSHeaders.BUCKET_NAME, bucketName);
        message.setHeader(OSSHeaders.OBJECT_KEY, objectKey);
        message.setHeader(OSSHeaders.LAST_MODIFIED, result.lastModified());
        message.setHeader(OSSHeaders.CONTENT_LENGTH, result.contentLength());
        message.setHeader(OSSHeaders.CONTENT_TYPE, result.contentType());
        message.setHeader(OSSHeaders.ETAG, result.eTag());
        message.setHeader(OSSHeaders.CONTENT_MD5, result.contentMd5());
        message.setHeader(OSSHeaders.FILE_NAME, objectKey);

        if (objectKey != null && objectKey.endsWith("/")) {
            message.setHeader(OSSHeaders.OBJECT_TYPE, OSSConstants.FOLDER);
        } else {
            message.setHeader(OSSHeaders.OBJECT_TYPE, OSSConstants.FILE);
        }
    }

    public static void mapOssObject(
            Exchange exchange, String bucketName, String objectKey, GetObjectResult result,
            byte[] body) {
        Message message = exchange.getIn();
        message.setBody(body);

        message.setHeader(OSSHeaders.BUCKET_NAME, bucketName);
        message.setHeader(OSSHeaders.OBJECT_KEY, objectKey);
        message.setHeader(OSSHeaders.LAST_MODIFIED, result.lastModified());
        message.setHeader(OSSHeaders.CONTENT_LENGTH, result.contentLength());
        message.setHeader(OSSHeaders.CONTENT_TYPE, result.contentType());
        message.setHeader(OSSHeaders.ETAG, result.eTag());
        message.setHeader(OSSHeaders.CONTENT_MD5, result.contentMd5());
        message.setHeader(OSSHeaders.FILE_NAME, objectKey);

        if (objectKey != null && objectKey.endsWith("/")) {
            message.setHeader(OSSHeaders.OBJECT_TYPE, OSSConstants.FOLDER);
        } else {
            message.setHeader(OSSHeaders.OBJECT_TYPE, OSSConstants.FILE);
        }
    }

    public static RuntimeCamelException wrapIOException(IOException e) {
        return new RuntimeCamelException(e);
    }
}
