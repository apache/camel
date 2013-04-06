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
package org.apache.camel.component.file;

import org.apache.camel.Exchange;

/**
 * Represents a pluggable strategy when processing files.
 */
public interface GenericFileProcessStrategy<T> {

    /**
     * Allows custom logic to be run on first poll preparing the strategy,
     * such as removing old lock files etc.
     *
     * @param operations file operations
     * @param endpoint   the endpoint
     * @throws Exception can be thrown in case of errors which causes poll to fail
     */
    void prepareOnStartup(GenericFileOperations<T> operations, GenericFileEndpoint<T> endpoint) throws Exception;

    /**
     * Called when work is about to begin on this file. This method may attempt
     * to acquire some file lock before returning true; returning false if the
     * file lock could not be obtained so that the file should be ignored.
     *
     * @param operations file operations
     * @param endpoint   the endpoint
     * @param exchange   the exchange
     * @param file       the file
     * @return true if the file can be processed (such as if a file lock could be obtained)
     * @throws Exception can be thrown in case of errors
     */
    boolean begin(GenericFileOperations<T> operations, GenericFileEndpoint<T> endpoint,
                  Exchange exchange, GenericFile<T> file) throws Exception;

    /**
     * Called when a begin is aborted, for example to release any resources which may have
     * been acquired during the {@link #begin(GenericFileOperations, GenericFileEndpoint, org.apache.camel.Exchange, GenericFile)}
     * operation.
     *
     * @param operations file operations
     * @param endpoint   the endpoint
     * @param exchange   the exchange
     * @param file       the file
     * @throws Exception can be thrown in case of errors
     */
    void abort(GenericFileOperations<T> operations, GenericFileEndpoint<T> endpoint,
               Exchange exchange, GenericFile<T> file) throws Exception;

    /**
     * Releases any file locks and possibly deletes or moves the file after
     * successful processing
     *
     * @param operations file operations
     * @param endpoint   the endpoint
     * @param exchange   the exchange
     * @param file       the file
     * @throws Exception can be thrown in case of errors
     */
    void commit(GenericFileOperations<T> operations, GenericFileEndpoint<T> endpoint,
                Exchange exchange, GenericFile<T> file) throws Exception;

    /**
     * Releases any file locks and possibly deletes or moves the file after
     * unsuccessful processing
     *
     * @param operations file operations
     * @param endpoint   the endpoint
     * @param exchange   the exchange
     * @param file       the file
     * @throws Exception can be thrown in case of errors
     */
    void rollback(GenericFileOperations<T> operations, GenericFileEndpoint<T> endpoint,
                  Exchange exchange, GenericFile<T> file) throws Exception;

}
