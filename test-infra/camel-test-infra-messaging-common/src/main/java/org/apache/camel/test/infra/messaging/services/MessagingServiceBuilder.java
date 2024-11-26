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

package org.apache.camel.test.infra.messaging.services;

import java.util.function.Function;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;

@Deprecated
public class MessagingServiceBuilder<T extends GenericContainer<T>> {
    private static final Logger LOG = LoggerFactory.getLogger(MessagingServiceBuilder.class);

    private Supplier<T> containerSupplier;
    private Function<T, String> endpointFunction;

    protected MessagingServiceBuilder() {
    }

    public static <T extends GenericContainer<T>> MessagingServiceBuilder<T> newBuilder(Supplier<T> containerSupplier) {
        MessagingServiceBuilder<T> messagingServiceBuilder = new MessagingServiceBuilder<>();

        messagingServiceBuilder.withContainer(containerSupplier);

        return messagingServiceBuilder;
    }

    protected MessagingServiceBuilder<T> withContainer(Supplier<T> containerSupplier) {
        this.containerSupplier = containerSupplier;

        return this;
    }

    public MessagingServiceBuilder<T> withEndpointProvider(Function<T, String> endpointFunction) {
        this.endpointFunction = endpointFunction;

        return this;
    }

    public MessagingService build() {
        String instanceType = System.getProperty("messaging.instance.type");

        if (instanceType == null || instanceType.isEmpty()) {
            LOG.info("Creating a new messaging local container service");
            return new MessagingLocalContainerService<>(containerSupplier.get(), this.endpointFunction);
        }

        if (instanceType.equals("remote")) {
            return new MessagingRemoteService();
        }

        throw new UnsupportedOperationException("Invalid messaging instance type");
    }
}
