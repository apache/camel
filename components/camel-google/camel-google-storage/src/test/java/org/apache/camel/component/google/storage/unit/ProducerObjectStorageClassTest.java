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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageClass;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.storage.GoogleCloudStorageComponent;
import org.apache.camel.component.google.storage.GoogleCloudStorageConstants;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The storage class of an uploaded object comes from the CamelGoogleCloudStorageStorageClass header, else from the
 * objectStorageClass option, else from the bucket.
 */
class ProducerObjectStorageClassTest extends GoogleCloudStorageBaseTest {

    @EndpointInject
    private ProducerTemplate template;

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:bucketDefault").to("google-storage://plainBucket?autoCreateBucket=true");
                from("direct:coldline")
                        .to("google-storage://archiveBucket?autoCreateBucket=true&storageClass=STANDARD"
                            + "&objectStorageClass=COLDLINE");
            }
        };
    }

    private Blob upload(String uri, String objectName, Object storageClassHeader) {
        Exchange exchange = template.request(uri, e -> {
            e.getIn().setHeader(GoogleCloudStorageConstants.OBJECT_NAME, objectName);
            if (storageClassHeader != null) {
                e.getIn().setHeader(GoogleCloudStorageConstants.METADATA_STORAGE_CLASS, storageClassHeader);
            }
            e.getIn().setBody(new ByteArrayInputStream("Hello".getBytes(StandardCharsets.UTF_8)));
        });
        assertThat(exchange.getException()).isNull();
        return exchange.getMessage().getBody(Blob.class);
    }

    private StorageClass storedClass(String bucket, String objectName) {
        Storage storage = context.getComponent("google-storage", GoogleCloudStorageComponent.class).getConfiguration()
                .getStorageClient();
        return storage.get(bucket, objectName).getStorageClass();
    }

    @Test
    void withoutOptionOrHeaderTheObjectHasNoStorageClassOfItsOwn() {
        upload("direct:bucketDefault", "plain.txt", null);

        assertThat(storedClass("plainBucket", "plain.txt")).isNull();
    }

    @Test
    void theOptionSetsTheStorageClassOfTheObject() {
        Blob blob = upload("direct:coldline", "archive.txt", null);

        assertThat(blob.getStorageClass()).isSameAs(StorageClass.COLDLINE);
        assertThat(storedClass("archiveBucket", "archive.txt")).isSameAs(StorageClass.COLDLINE);
    }

    @Test
    void theHeaderTakesPrecedenceOverTheOption() {
        upload("direct:coldline", "header.txt", StorageClass.ARCHIVE);

        assertThat(storedClass("archiveBucket", "header.txt")).isSameAs(StorageClass.ARCHIVE);
    }

    @Test
    void theHeaderCanBeAName() {
        upload("direct:bucketDefault", "named.txt", "nearline");

        assertThat(storedClass("plainBucket", "named.txt")).isSameAs(StorageClass.NEARLINE);
    }
}
