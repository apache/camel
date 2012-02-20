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
package org.apache.camel.model;

import java.util.concurrent.ExecutorService;

import org.apache.camel.ExecutorServiceAware;

/**
 * Enables definitions to support concurrency using {@link java.util.concurrent.ExecutorService}
 *
 * @version 
 */
public interface ExecutorServiceAwareDefinition<Type extends ProcessorDefinition<?>> extends ExecutorServiceAware {

    /**
     * Setting the executor service for executing
     *
     * @param executorService the executor service
     * @return the builder
     */
    Type executorService(ExecutorService executorService);

    /**
     * Setting the executor service for executing
     *
     * @param executorServiceRef reference for a {@link java.util.concurrent.ExecutorService}
     *                           to lookup in the {@link org.apache.camel.spi.Registry}
     * @return the builder
     */
    Type executorServiceRef(String executorServiceRef);

}
