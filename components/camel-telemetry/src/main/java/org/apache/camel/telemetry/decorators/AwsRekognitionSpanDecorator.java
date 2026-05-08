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

public class AwsRekognitionSpanDecorator extends AbstractSpanDecorator {

    static final String REKOGNITION_OPERATION = "operation";
    static final String REKOGNITION_COLLECTION_ID = "collectionId";
    static final String REKOGNITION_JOB_ID = "jobId";
    static final String REKOGNITION_JOB_NAME = "jobName";
    static final String REKOGNITION_FACE_ID = "faceId";

    /**
     * Constants copied from {@link org.apache.camel.component.aws2.rekognition.Rekognition2Constants}
     */
    static final String OPERATION = "CamelAwsRekognitionOperation";
    static final String COLLECTION_ID = "CamelAwsRekognitionCollectionId";
    static final String JOB_ID = "CamelAwsRekognitionJobId";
    static final String JOB_NAME = "CamelAwsRekognitionJobName";
    static final String FACE_ID = "CamelAwsRekognitionFaceId";

    @Override
    public String getComponent() {
        return "aws2-rekognition";
    }

    @Override
    public String getComponentClassName() {
        return "org.apache.camel.component.aws2.rekognition.Rekognition2Component";
    }

    @Override
    public void beforeTracingEvent(Span span, Exchange exchange, Endpoint endpoint) {
        super.beforeTracingEvent(span, exchange, endpoint);

        String operation = exchange.getIn().getHeader(OPERATION, String.class);
        if (operation != null) {
            span.setTag(REKOGNITION_OPERATION, operation);
        }

        String collectionId = exchange.getIn().getHeader(COLLECTION_ID, String.class);
        if (collectionId != null) {
            span.setTag(REKOGNITION_COLLECTION_ID, collectionId);
        }

        String jobId = exchange.getIn().getHeader(JOB_ID, String.class);
        if (jobId != null) {
            span.setTag(REKOGNITION_JOB_ID, jobId);
        }

        String jobName = exchange.getIn().getHeader(JOB_NAME, String.class);
        if (jobName != null) {
            span.setTag(REKOGNITION_JOB_NAME, jobName);
        }

        String faceId = exchange.getIn().getHeader(FACE_ID, String.class);
        if (faceId != null) {
            span.setTag(REKOGNITION_FACE_ID, faceId);
        }
    }

}
