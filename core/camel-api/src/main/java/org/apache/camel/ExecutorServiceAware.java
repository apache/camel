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

import java.util.concurrent.ExecutorService;

/**
 * Is used for easy configuration of {@link ExecutorService}.
 */
public interface ExecutorServiceAware {

    /**
     * Gets the executor service
     *
     * @return the executor
     */
    ExecutorService getExecutorService();

    /**
     * Sets the executor service to be used.
     *
     * @param executorService the executor
     */
    void setExecutorService(ExecutorService executorService);

    /**
     * Gets the reference to lookup in the {@link org.apache.camel.spi.Registry} for the executor service to be used.
     *
     * @return the reference, or <tt>null</tt> if the executor was set directly
     */
    String getExecutorServiceRef();

    /**
     * Sets a reference to lookup in the {@link org.apache.camel.spi.Registry} for the executor service to be used.
     *
     * @param executorServiceRef reference for the executor
     */
    void setExecutorServiceRef(String executorServiceRef);

}
