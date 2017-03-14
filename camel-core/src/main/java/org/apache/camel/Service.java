/**
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

import java.io.Closeable;
import java.io.IOException;

/**
 * Represents the core lifecycle API for POJOs which can be started and stopped
 * 
 * @version 
 */
public interface Service extends Closeable {

    /**
     * Starts the service
     * 
     * @throws Exception is thrown if starting failed
     */
    void start() throws Exception;

    /**
     * Stops the service
     * 
     * @throws Exception is thrown if stopping failed
     */
    void stop() throws Exception;

    /**
     * Delegates to {@link Service#stop()} so it can be used in
     * try-with-resources expression.
     * 
     * @throws IOException per contract of {@link Closeable} if
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
