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

public class AwsTextractSpanDecorator extends AbstractSpanDecorator {

    static final String TEXTRACT_OPERATION = "operation";
    static final String TEXTRACT_S3_BUCKET = "s3Bucket";
    static final String TEXTRACT_S3_OBJECT = "s3Object";
    static final String TEXTRACT_JOB_ID = "jobId";

    /**
     * Constants copied from {@link org.apache.camel.component.aws2.textract.Textract2Constants}
     */
    static final String OPERATION = "CamelAwsTextractOperation";
    static final String S3_BUCKET = "CamelAwsTextractS3Bucket";
    static final String S3_OBJECT = "CamelAwsTextractS3Object";
    static final String JOB_ID = "CamelAwsTextractJobId";

    @Override
    public String getComponent() {
        return "aws2-textract";
    }

    @Override
    public String getComponentClassName() {
        return "org.apache.camel.component.aws2.textract.Textract2Component";
    }

    @Override
    public void beforeTracingEvent(Span span, Exchange exchange, Endpoint endpoint) {
        super.beforeTracingEvent(span, exchange, endpoint);

        String operation = exchange.getIn().getHeader(OPERATION, String.class);
        if (operation != null) {
            span.setTag(TEXTRACT_OPERATION, operation);
        }

        String s3Bucket = exchange.getIn().getHeader(S3_BUCKET, String.class);
        if (s3Bucket != null) {
            span.setTag(TEXTRACT_S3_BUCKET, s3Bucket);
        }

        String s3Object = exchange.getIn().getHeader(S3_OBJECT, String.class);
        if (s3Object != null) {
            span.setTag(TEXTRACT_S3_OBJECT, s3Object);
        }

        String jobId = exchange.getIn().getHeader(JOB_ID, String.class);
        if (jobId != null) {
            span.setTag(TEXTRACT_JOB_ID, jobId);
        }
    }

}
