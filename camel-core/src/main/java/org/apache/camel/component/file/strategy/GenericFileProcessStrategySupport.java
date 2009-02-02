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
package org.apache.camel.component.file.strategy;

import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileEndpoint;
import org.apache.camel.component.file.GenericFileExchange;
import org.apache.camel.component.file.GenericFileExclusiveReadLockStrategy;
import org.apache.camel.component.file.GenericFileOperations;
import org.apache.camel.component.file.GenericFileProcessStrategy;

public abstract class GenericFileProcessStrategySupport<T> implements GenericFileProcessStrategy<T> {
    private GenericFileExclusiveReadLockStrategy exclusiveReadLockStrategy;

    @SuppressWarnings("unchecked")
    public boolean begin(GenericFileOperations<T> operations, GenericFileEndpoint<T> endpoint, GenericFileExchange<T> exchange, GenericFile<T> file) throws Exception {
        // is we use excluse read then acquire the exclusive read (waiting until we got it)
        if (exclusiveReadLockStrategy != null) {
            boolean lock = exclusiveReadLockStrategy.acquireExclusiveReadLock(operations, file, exchange);
            if (!lock) {
                // do not begin sice we could not get the exclusive read lcok
                return false;
            }
        }

        return true;
    }

    @SuppressWarnings("unchecked")
    public void commit(GenericFileOperations<T> operations, GenericFileEndpoint<T> endpoint, GenericFileExchange<T> exchange, GenericFile<T> file) throws Exception {
        if (exclusiveReadLockStrategy != null) {
            exclusiveReadLockStrategy.releaseExclusiveReadLock(operations, file, exchange);
        }
    }

    @SuppressWarnings("unchecked")
    public void rollback(GenericFileOperations<T> operations, GenericFileEndpoint<T> endpoint, GenericFileExchange<T> exchange, GenericFile<T> file) throws Exception {
        if (exclusiveReadLockStrategy != null) {
            exclusiveReadLockStrategy.releaseExclusiveReadLock(operations, file, exchange);
        }
    }

    public GenericFileExclusiveReadLockStrategy getExclusiveReadLockStrategy() {
        return exclusiveReadLockStrategy;
    }

    public void setExclusiveReadLockStrategy(GenericFileExclusiveReadLockStrategy exclusiveReadLockStrategy) {
        this.exclusiveReadLockStrategy = exclusiveReadLockStrategy;
    }
}

