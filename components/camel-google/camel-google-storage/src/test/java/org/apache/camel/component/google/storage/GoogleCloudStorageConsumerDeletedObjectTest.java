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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.Storage;
import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.storage.localstorage.LocalStorageHelper;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.PollingConsumerPollStrategy;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An object deleted (for example by another consumer) after the listing and before its content is downloaded is
 * skipped, and the other objects of the poll are consumed.
 */
class GoogleCloudStorageConsumerDeletedObjectTest extends CamelTestSupport {

    private final AtomicInteger failedPolls = new AtomicInteger();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        Storage delegate = LocalStorageHelper.getOptions().getService();
        delegate.create(BucketInfo.of("myCamelBucket"));
        for (String name : new String[] { "a.txt", "b.txt", "c.txt" }) {
            delegate.create(BlobInfo.newBuilder("myCamelBucket", name).build(), name.getBytes(StandardCharsets.UTF_8));
        }
        AtomicBoolean deleted = new AtomicBoolean();
        // the listed blobs download their content through the delegate, in which b.txt no longer exists
        Storage storage = (Storage) Proxy.newProxyInstance(Storage.class.getClassLoader(), new Class<?>[] { Storage.class },
                (proxy, method, args) -> {
                    Object result;
                    try {
                        result = method.invoke(delegate, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                    if ("list".equals(method.getName()) && deleted.compareAndSet(false, true)) {
                        delegate.delete("myCamelBucket", "b.txt");
                    }
                    return result;
                });
        context.getComponent("google-storage", GoogleCloudStorageComponent.class).getConfiguration()
                .setStorageClient(storage);
        context.getRegistry().bind("countFailedPolls", new PollingConsumerPollStrategy() {
            @Override
            public boolean begin(Consumer consumer, Endpoint endpoint) {
                return true;
            }

            @Override
            public void commit(Consumer consumer, Endpoint endpoint, int polledMessages) {
                // only failed polls are counted
            }

            @Override
            public boolean rollback(Consumer consumer, Endpoint endpoint, int retryCounter, Exception e) {
                failedPolls.incrementAndGet();
                return false;
            }
        });
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("google-storage://myCamelBucket?delay=10&pollStrategy=#countFailedPolls")
                        .to("mock:result");
            }
        };
    }

    @Test
    void deletedObjectIsSkipped() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceivedInAnyOrder("a.txt", "c.txt");
        // the batch is built after the downloads, so it only counts the objects that are consumed
        mock.allMessages().exchangeProperty(ExchangePropertyKey.BATCH_SIZE.getName()).isEqualTo(2);

        mock.assertIsSatisfied();
        assertThat(failedPolls)
                .as("the poll failed because b.txt was deleted between the listing and the download")
                .hasValue(0);
    }
}
