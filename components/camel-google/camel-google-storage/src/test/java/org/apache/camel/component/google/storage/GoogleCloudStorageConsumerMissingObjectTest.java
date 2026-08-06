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
package org.apache.camel.component.google.storage;

import org.apache.camel.CamelContext;
import org.apache.camel.component.google.storage.localstorage.LocalStorageHelper;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Verifies that polling for a configured object name that does not exist in the bucket is a no-op instead of failing.
 */
class GoogleCloudStorageConsumerMissingObjectTest extends CamelTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        GoogleCloudStorageComponent component = context.getComponent("google-storage", GoogleCloudStorageComponent.class);
        component.getConfiguration().setStorageClient(LocalStorageHelper.getOptions().getService());
        return context;
    }

    private GoogleCloudStorageConsumer createConsumer(String objectName) throws Exception {
        GoogleCloudStorageEndpoint endpoint = context.getEndpoint(
                "google-storage://myCamelBucket?autoCreateBucket=true&objectName=" + objectName,
                GoogleCloudStorageEndpoint.class);
        GoogleCloudStorageConsumer consumer = (GoogleCloudStorageConsumer) endpoint.createConsumer(exchange -> {
        });
        endpoint.start();
        return consumer;
    }

    @Test
    void pollingAMissingObjectYieldsNoExchange() throws Exception {
        GoogleCloudStorageConsumer consumer = createConsumer("there-is-no-such-object.txt");

        // the object is not in the bucket, so the storage client hands back a null blob
        assertThatNoException().isThrownBy(consumer::poll);
        assertThat(consumer.poll()).isZero();
    }
}
