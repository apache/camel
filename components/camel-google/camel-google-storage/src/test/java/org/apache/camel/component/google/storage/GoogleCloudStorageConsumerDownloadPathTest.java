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
import org.apache.camel.Exchange;
import org.apache.camel.component.google.storage.localstorage.LocalStorageHelper;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Verifies that the local download path the consumer builds from a remote object name stays within the configured
 * {@code downloadFileName} directory.
 */
class GoogleCloudStorageConsumerDownloadPathTest extends CamelTestSupport {

    private static final String DOWNLOAD_DIR = "target/gcs-download-path";

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        GoogleCloudStorageComponent component = context.getComponent("google-storage", GoogleCloudStorageComponent.class);
        component.getConfiguration().setStorageClient(LocalStorageHelper.getOptions().getService());
        return context;
    }

    private GoogleCloudStorageConsumer createConsumer(String downloadFileName) throws Exception {
        GoogleCloudStorageEndpoint endpoint = context.getEndpoint(
                "google-storage://myCamelBucket?autoCreateBucket=true&downloadFileName=" + downloadFileName,
                GoogleCloudStorageEndpoint.class);
        return (GoogleCloudStorageConsumer) endpoint.createConsumer(exchange -> {
        });
    }

    @Test
    void plainObjectNameResolvesInsideDownloadDirectory() throws Exception {
        GoogleCloudStorageConsumer consumer = createConsumer(DOWNLOAD_DIR);
        Exchange exchange = new DefaultExchange(context);

        assertThat(consumer.evaluateFileExpression(exchange, DOWNLOAD_DIR, "file.txt"))
                .isEqualTo(DOWNLOAD_DIR + "/file.txt");
    }

    @Test
    void nestedObjectNameResolvesInsideDownloadDirectory() throws Exception {
        GoogleCloudStorageConsumer consumer = createConsumer(DOWNLOAD_DIR);
        Exchange exchange = new DefaultExchange(context);

        assertThat(consumer.evaluateFileExpression(exchange, DOWNLOAD_DIR, "nested/file.txt"))
                .isEqualTo(DOWNLOAD_DIR + "/nested/file.txt");
    }

    @Test
    void objectNameWithParentSegmentIsRejected() throws Exception {
        GoogleCloudStorageConsumer consumer = createConsumer(DOWNLOAD_DIR);
        Exchange exchange = new DefaultExchange(context);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> consumer.evaluateFileExpression(exchange, DOWNLOAD_DIR, "../escape.txt"))
                .withMessageContaining("../escape.txt");
    }

    @Test
    void objectNameWithParentSegmentNestedInTheKeyIsRejected() throws Exception {
        GoogleCloudStorageConsumer consumer = createConsumer(DOWNLOAD_DIR);
        Exchange exchange = new DefaultExchange(context);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> consumer.evaluateFileExpression(exchange, DOWNLOAD_DIR, "nested/../../escape.txt"));
    }

    @Test
    void routeAuthorSuppliedExpressionIsNotConfined() throws Exception {
        // a downloadFileName that already contains an expression is built by the route author, who is trusted, so it is
        // evaluated as configured and deliberately left outside the containment check
        String expression = "target/${file:name}";
        GoogleCloudStorageConsumer consumer = createConsumer(DOWNLOAD_DIR);
        Exchange exchange = new DefaultExchange(context);

        assertThat(consumer.evaluateFileExpression(exchange, expression, "../escape.txt"))
                .isEqualTo("target/../escape.txt");
    }
}
