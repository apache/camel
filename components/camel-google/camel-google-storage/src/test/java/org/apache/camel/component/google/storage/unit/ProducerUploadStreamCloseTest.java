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
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import com.google.cloud.storage.Storage;
import org.apache.camel.Exchange;
import org.apache.camel.component.google.storage.GoogleCloudStorageComponent;
import org.apache.camel.component.google.storage.GoogleCloudStorageConfiguration;
import org.apache.camel.component.google.storage.GoogleCloudStorageConstants;
import org.apache.camel.component.google.storage.GoogleCloudStorageEndpoint;
import org.apache.camel.component.google.storage.GoogleCloudStorageProducer;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProducerUploadStreamCloseTest {

    @TempDir
    Path directory;

    @Test
    void closesTheFileStreamWhenTheUploadFails() throws Exception {
        Path file = Files.writeString(directory.resolve("upload.txt"), "payload");
        AtomicReference<InputStream> uploaded = new AtomicReference<>();
        IOException failure = new IOException("Upload failed");

        try (DefaultCamelContext context = new DefaultCamelContext()) {
            GoogleCloudStorageProducer producer = producer(context, uploaded, failure);
            Exchange exchange = new DefaultExchange(context);
            exchange.getIn().setBody(file.toFile());

            assertThatThrownBy(() -> producer.process(exchange)).isSameAs(failure);
            assertThat(uploaded.get()).isNotNull();
            assertThatThrownBy(() -> uploaded.get().read()).isInstanceOf(IOException.class);
        }
    }

    @Test
    void closesTheBodyStreamWhenTheUploadFails() throws Exception {
        TrackingStream stream = new TrackingStream();
        IOException failure = new IOException("Upload failed");

        try (DefaultCamelContext context = new DefaultCamelContext()) {
            GoogleCloudStorageProducer producer = producer(context, new AtomicReference<>(), failure);
            Exchange exchange = new DefaultExchange(context);
            exchange.getIn().setBody(stream);
            exchange.getIn().setHeader(GoogleCloudStorageConstants.CONTENT_LENGTH, 7);

            assertThatThrownBy(() -> producer.process(exchange)).isSameAs(failure);
            assertThat(stream.closed).isTrue();
        }
    }

    @Test
    void stillClosesTheBodyStreamAfterASuccessfulUpload() throws Exception {
        TrackingStream stream = new TrackingStream();
        AtomicReference<InputStream> uploaded = new AtomicReference<>();

        try (DefaultCamelContext context = new DefaultCamelContext()) {
            GoogleCloudStorageProducer producer = producer(context, uploaded, null);
            Exchange exchange = new DefaultExchange(context);
            exchange.getIn().setBody(stream);
            exchange.getIn().setHeader(GoogleCloudStorageConstants.CONTENT_LENGTH, 7);

            producer.process(exchange);

            assertThat(uploaded.get()).isSameAs(stream);
            assertThat(stream.closed).isTrue();
        }
    }

    private static GoogleCloudStorageProducer producer(
            DefaultCamelContext context, AtomicReference<InputStream> uploaded, IOException failure) {
        Storage storage = (Storage) Proxy.newProxyInstance(Storage.class.getClassLoader(), new Class<?>[] { Storage.class },
                (proxy, method, args) -> {
                    if ("createFrom".equals(method.getName())) {
                        uploaded.set((InputStream) args[1]);
                        if (failure != null) {
                            throw failure;
                        }
                        return null;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        GoogleCloudStorageConfiguration configuration = new GoogleCloudStorageConfiguration();
        configuration.setBucketName("bucket");
        configuration.setObjectName("object");
        GoogleCloudStorageComponent component = new GoogleCloudStorageComponent(context);
        GoogleCloudStorageEndpoint endpoint
                = new GoogleCloudStorageEndpoint("google-storage://bucket", component, configuration) {
                    @Override
                    public Storage getStorageClient() {
                        return storage;
                    }
                };
        return new GoogleCloudStorageProducer(endpoint);
    }

    private static final class TrackingStream extends ByteArrayInputStream {
        private boolean closed;

        private TrackingStream() {
            super(new byte[7]);
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
