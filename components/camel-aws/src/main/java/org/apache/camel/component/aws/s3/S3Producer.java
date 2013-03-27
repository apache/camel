/**
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

import java.io.InputStream;
import java.util.Date;
import java.util.Map;

import com.amazonaws.services.s3.model.AccessControlList;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.PutObjectResult;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Producer which sends messages to the Amazon Web Service Simple Storage Service <a
 * href="http://aws.amazon.com/s3/">AWS S3</a>
 */
public class S3Producer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(S3Producer.class);

    public S3Producer(final Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(final Exchange exchange) throws Exception {
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

        Map<String, String> userMetadata = exchange.getIn().getHeader(S3Constants.USER_METADATA, Map.class);
        if (userMetadata != null) {
            objectMetadata.setUserMetadata(userMetadata);
        }

        PutObjectRequest putObjectRequest = new PutObjectRequest(getConfiguration().getBucketName(),
                determineKey(exchange), exchange.getIn().getMandatoryBody(InputStream.class), objectMetadata);

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
            // note: if cannedacl and acl are both specified the last one will be used. refer to
            // PutObjectRequest#setAccessControlList for more details
            putObjectRequest.setAccessControlList(acl);
        }
        LOG.trace("Put object [{}] from exchange [{}]...", putObjectRequest, exchange);

        PutObjectResult putObjectResult = getEndpoint().getS3Client().putObject(putObjectRequest);

        LOG.trace("Received result [{}]", putObjectResult);

        Message message = getMessageForResponse(exchange);
        message.setHeader(S3Constants.E_TAG, putObjectResult.getETag());
        if (putObjectResult.getVersionId() != null) {
            message.setHeader(S3Constants.VERSION_ID, putObjectResult.getVersionId());
        }
    }

    private String determineKey(final Exchange exchange) {
        String key = exchange.getIn().getHeader(S3Constants.KEY, String.class);
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

    private Message getMessageForResponse(final Exchange exchange) {
        if (exchange.getPattern().isOutCapable()) {
            Message out = exchange.getOut();
            out.copyFrom(exchange.getIn());
            return out;
        }

        return exchange.getIn();
    }

    protected S3Configuration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    @Override
    public String toString() {
        return "S3Producer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
    }

    @Override
    public S3Endpoint getEndpoint() {
        return (S3Endpoint) super.getEndpoint();
    }
}