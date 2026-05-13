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
package org.apache.camel.telemetry.decorators;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.telemetry.Span;

public class AwsS3SpanDecorator extends AbstractSpanDecorator {

    static final String S3_BUCKET_NAME = "bucketName";
    static final String S3_KEY = "key";
    static final String S3_VERSION_ID = "versionId";
    static final String S3_OPERATION = "operation";
    static final String S3_STORAGE_CLASS = "storageClass";

    /**
     * Constants copied from {@link org.apache.camel.component.aws2.s3.AWS2S3Constants}
     */
    static final String BUCKET_NAME = "CamelAwsS3BucketName";
    static final String KEY = "CamelAwsS3Key";
    static final String VERSION_ID = "CamelAwsS3VersionId";
    static final String OPERATION = "CamelAwsS3Operation";
    static final String STORAGE_CLASS = "CamelAwsS3StorageClass";

    @Override
    public String getComponent() {
        return "aws2-s3";
    }

    @Override
    public String getComponentClassName() {
        return "org.apache.camel.component.aws2.s3.AWS2S3Component";
    }

    @Override
    public void beforeTracingEvent(Span span, Exchange exchange, Endpoint endpoint) {
        super.beforeTracingEvent(span, exchange, endpoint);

        String bucketName = exchange.getIn().getHeader(BUCKET_NAME, String.class);
        if (bucketName != null) {
            span.setTag(S3_BUCKET_NAME, bucketName);
        }

        String key = exchange.getIn().getHeader(KEY, String.class);
        if (key != null) {
            span.setTag(S3_KEY, key);
        }

        String versionId = exchange.getIn().getHeader(VERSION_ID, String.class);
        if (versionId != null) {
            span.setTag(S3_VERSION_ID, versionId);
        }

        String operation = exchange.getIn().getHeader(OPERATION, String.class);
        if (operation != null) {
            span.setTag(S3_OPERATION, operation);
        }

        String storageClass = exchange.getIn().getHeader(STORAGE_CLASS, String.class);
        if (storageClass != null) {
            span.setTag(S3_STORAGE_CLASS, storageClass);
        }
    }

}
