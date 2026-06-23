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

public class AwsS3VectorsSpanDecorator extends AbstractSpanDecorator {

    static final String S3_VECTORS_OPERATION = "operation";
    static final String S3_VECTORS_VECTOR_BUCKET_NAME = "vectorBucketName";
    static final String S3_VECTORS_VECTOR_INDEX_NAME = "vectorIndexName";
    static final String S3_VECTORS_VECTOR_ID = "vectorId";

    /**
     * Constants copied from {@link org.apache.camel.component.aws2.s3vectors.AWS2S3VectorsConstants}
     */
    static final String OPERATION = "CamelAwsS3VectorsOperation";
    static final String VECTOR_BUCKET_NAME = "CamelAwsS3VectorsVectorBucketName";
    static final String VECTOR_INDEX_NAME = "CamelAwsS3VectorsVectorIndexName";
    static final String VECTOR_ID = "CamelAwsS3VectorsVectorId";

    @Override
    public String getComponent() {
        return "aws2-s3-vectors";
    }

    @Override
    public String getComponentClassName() {
        return "org.apache.camel.component.aws2.s3vectors.AWS2S3VectorsComponent";
    }

    @Override
    public void beforeTracingEvent(Span span, Exchange exchange, Endpoint endpoint) {
        super.beforeTracingEvent(span, exchange, endpoint);

        String operation = exchange.getIn().getHeader(OPERATION, String.class);
        if (operation != null) {
            span.setTag(S3_VECTORS_OPERATION, operation);
        }

        String vectorBucketName = exchange.getIn().getHeader(VECTOR_BUCKET_NAME, String.class);
        if (vectorBucketName != null) {
            span.setTag(S3_VECTORS_VECTOR_BUCKET_NAME, vectorBucketName);
        }

        String vectorIndexName = exchange.getIn().getHeader(VECTOR_INDEX_NAME, String.class);
        if (vectorIndexName != null) {
            span.setTag(S3_VECTORS_VECTOR_INDEX_NAME, vectorIndexName);
        }

        String vectorId = exchange.getIn().getHeader(VECTOR_ID, String.class);
        if (vectorId != null) {
            span.setTag(S3_VECTORS_VECTOR_ID, vectorId);
        }
    }

}
