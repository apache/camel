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
package org.apache.camel;

import java.io.IOException;

/**
 * Represents the core lifecycle API for services which can be initialized, started and stopped
 */
public interface Service extends AutoCloseable {

    /**
     * Optional build phase which is executed by frameworks that supports pre-building projects (pre-compile)
     * which allows special optimizations such as camel-quarkus.
     *
     * @throws RuntimeCamelException is thrown if build failed
     */
    default void build() {
    }

    /**
     * Initialize the service
     *
     * @throws RuntimeCamelException is thrown if initialization failed
     */
    default void init() {
    }

    /**
     * Starts the service
     *
     * @throws RuntimeCamelException is thrown if starting failed
     */
    void start();

    /**
     * Stops the service
     *
     * @throws RuntimeCamelException is thrown if stopping failed
     */
    void stop();

    /**
     * Delegates to {@link Service#stop()} so it can be used in
     * try-with-resources expression.
     * 
     * @throws IOException per contract of {@link AutoCloseable} if
     *             {@link Service#stop()} fails
     */
    default void close() throws IOException {
        try {
            stop();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }
}
