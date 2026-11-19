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

import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageClass;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.component.google.storage.GoogleCloudStorageComponent;
import org.apache.camel.component.google.storage.GoogleCloudStorageEndpoint;
import org.apache.camel.support.PropertyBindingSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The storageClass option is set from the name of a storage class, in an endpoint URI or as a property.
 */
public class GoogleCloudStorageClassConverterTest extends GoogleCloudStorageBaseTest {

    @Test
    public void testStorageClassFromTheUri() {
        GoogleCloudStorageEndpoint endpoint = context.getEndpoint(
                "google-storage://coldbucket?autoCreateBucket=false&storageClass=NEARLINE", GoogleCloudStorageEndpoint.class);

        assertThat(endpoint.getConfiguration().getStorageClass()).isSameAs(StorageClass.NEARLINE);
    }

    @Test
    public void testStorageClassNameIsNotCaseSensitive() {
        GoogleCloudStorageEndpoint endpoint = context.getEndpoint(
                "google-storage://coldbucket?autoCreateBucket=false&storageClass=coldline", GoogleCloudStorageEndpoint.class);

        assertThat(endpoint.getConfiguration().getStorageClass()).isSameAs(StorageClass.COLDLINE);
    }

    @Test
    public void testStorageClassOnTheComponent() {
        GoogleCloudStorageComponent component = context.getComponent("google-storage", GoogleCloudStorageComponent.class);
        // as camel.component.google-storage.storageClass=ARCHIVE binds it
        PropertyBindingSupport.build().withConfigurer(component.getComponentPropertyConfigurer())
                .bind(context, component, "storageClass", "ARCHIVE");

        assertThat(component.getConfiguration().getStorageClass()).isSameAs(StorageClass.ARCHIVE);
    }

    @Test
    public void testUnknownStorageClassFails() {
        assertThatThrownBy(() -> context.getEndpoint("google-storage://coldbucket?autoCreateBucket=false&storageClass=FROZEN"))
                .isInstanceOf(ResolveEndpointFailedException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testAutoCreatedBucketGetsTheStorageClass() {
        // the endpoint creates the bucket when it starts
        context.getEndpoint("google-storage://nearlinebucket?autoCreateBucket=true&storageClass=NEARLINE&storageLocation=EU");

        Storage storage = context.getComponent("google-storage", GoogleCloudStorageComponent.class).getConfiguration()
                .getStorageClient();
        Bucket bucket = storage.get("nearlinebucket");
        assertThat(bucket).isNotNull();
        assertThat(bucket.getStorageClass()).isSameAs(StorageClass.NEARLINE);
    }
}
