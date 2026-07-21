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

public class GoogleStorageSpanDecorator extends AbstractSpanDecorator {

    static final String STORAGE_OPERATION = "operation";
    static final String STORAGE_BUCKET_NAME = "bucketName";
    static final String STORAGE_OBJECT_NAME = "objectName";
    static final String STORAGE_DESTINATION_BUCKET_NAME = "destinationBucketName";
    static final String STORAGE_DESTINATION_OBJECT_NAME = "destinationObjectName";

    /**
     * Constants copied from {@link org.apache.camel.component.google.storage.GoogleCloudStorageConstants}
     */
    static final String OPERATION = "CamelGoogleCloudStorageOperation";
    static final String BUCKET_NAME = "CamelGoogleCloudStorageBucketName";
    static final String OBJECT_NAME = "CamelGoogleCloudStorageObjectName";
    static final String DESTINATION_OBJECT_NAME = "CamelGoogleCloudStorageDestinationObjectName";
    static final String DESTINATION_BUCKET_NAME = "CamelGoogleCloudStorageDestinationBucketName";

    @Override
    public String getComponent() {
        return "google-storage";
    }

    @Override
    public String getComponentClassName() {
        return "org.apache.camel.component.google.storage.GoogleCloudStorageComponent";
    }

    @Override
    public void beforeTracingEvent(Span span, Exchange exchange, Endpoint endpoint) {
        super.beforeTracingEvent(span, exchange, endpoint);

        Object operation = exchange.getIn().getHeader(OPERATION);
        if (operation != null) {
            span.setTag(STORAGE_OPERATION, operation.toString());
        }

        String bucketName = exchange.getIn().getHeader(BUCKET_NAME, String.class);
        if (bucketName != null) {
            span.setTag(STORAGE_BUCKET_NAME, bucketName);
        }

        String objectName = exchange.getIn().getHeader(OBJECT_NAME, String.class);
        if (objectName != null) {
            span.setTag(STORAGE_OBJECT_NAME, objectName);
        }

        String destinationBucketName = exchange.getIn().getHeader(DESTINATION_BUCKET_NAME, String.class);
        if (destinationBucketName != null) {
            span.setTag(STORAGE_DESTINATION_BUCKET_NAME, destinationBucketName);
        }

        String destinationObjectName = exchange.getIn().getHeader(DESTINATION_OBJECT_NAME, String.class);
        if (destinationObjectName != null) {
            span.setTag(STORAGE_DESTINATION_OBJECT_NAME, destinationObjectName);
        }
    }

}
