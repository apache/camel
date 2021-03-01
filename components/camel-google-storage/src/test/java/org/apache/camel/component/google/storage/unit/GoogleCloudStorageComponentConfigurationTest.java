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
package org.apache.camel.component.google.storage.unit;

import org.apache.camel.component.google.storage.GoogleCloudStorageComponent;
import org.apache.camel.component.google.storage.GoogleCloudStorageConfiguration;
import org.apache.camel.component.google.storage.GoogleCloudStorageEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class GoogleCloudStorageComponentConfigurationTest extends CamelTestSupport {

    @Test
    public void createEndpointWithMinimalConfiguration() throws Exception {
        final String bucketName = "myCamelBucket";
        final String serviceAccountKeyFile = "somefile.json";

        GoogleCloudStorageComponent component = context.getComponent("google-storage", GoogleCloudStorageComponent.class);
        GoogleCloudStorageEndpoint endpoint = (GoogleCloudStorageEndpoint) component.createEndpoint(
                String.format("google-storage://%s?serviceAccountKey=file:%s", bucketName, serviceAccountKeyFile));

        assertEquals(endpoint.getConfiguration().getBucketName(), bucketName);
        assertEquals(endpoint.getConfiguration().getServiceAccountKey(), "file:" + serviceAccountKeyFile);
    }

    public void createEndpointForComplexConsumer() throws Exception {

        final String bucketName = "sourceCamelBucket";
        final String serviceAccountKeyFile = "somefile.json";
        final boolean moveAfterRead = false;
        final String destinationBucket = "destinationCamelBucket";
        final boolean autoCreateBucket = true;
        final boolean deleteAfterRead = false;
        final boolean includeBody = true;

        GoogleCloudStorageComponent component = context.getComponent("google-storage", GoogleCloudStorageComponent.class);
        GoogleCloudStorageEndpoint endpoint = (GoogleCloudStorageEndpoint) component.createEndpoint(
                String.format(
                        "google-storage://%s?serviceAccountKey=file:%s&moveAfterRead=%s&destinationBucket=%s&autoCreateBucket=%s&deleteAfterRead=%s&includeBody=%s",
                        bucketName, serviceAccountKeyFile, moveAfterRead, destinationBucket, autoCreateBucket,
                        deleteAfterRead, includeBody));

        GoogleCloudStorageConfiguration configuration = endpoint.getConfiguration();
        assertEquals(configuration.getBucketName(), bucketName);
        assertEquals(configuration.getServiceAccountKey(), "file:" + serviceAccountKeyFile);
        assertEquals(configuration.isMoveAfterRead(), moveAfterRead);
        assertEquals(configuration.getDestinationBucket(), destinationBucket);
        assertEquals(configuration.isAutoCreateBucket(), autoCreateBucket);
        assertEquals(configuration.isDeleteAfterRead(), deleteAfterRead);
        assertEquals(configuration.isIncludeBody(), includeBody);

    }

}
