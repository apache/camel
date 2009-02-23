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

import java.io.File;

import org.apache.camel.Exchange;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileEndpoint;
import org.apache.camel.component.file.GenericFileExchange;
import org.apache.camel.component.file.GenericFileExclusiveReadLockStrategy;
import org.apache.camel.component.file.GenericFileOperations;
import org.apache.camel.component.file.GenericFileProcessStrategy;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public abstract class GenericFileProcessStrategySupport<T> implements GenericFileProcessStrategy<T> {
    protected final transient Log log = LogFactory.getLog(getClass());
    protected GenericFileExclusiveReadLockStrategy<T> exclusiveReadLockStrategy;

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

    public void commit(GenericFileOperations<T> operations, GenericFileEndpoint<T> endpoint, GenericFileExchange<T> exchange, GenericFile<T> file) throws Exception {
        if (exclusiveReadLockStrategy != null) {
            exclusiveReadLockStrategy.releaseExclusiveReadLock(operations, file, exchange);
        }

        deleteLocalWorkFile(exchange);
    }

    public void rollback(GenericFileOperations<T> operations, GenericFileEndpoint<T> endpoint, GenericFileExchange<T> exchange, GenericFile<T> file) throws Exception {
        if (exclusiveReadLockStrategy != null) {
            exclusiveReadLockStrategy.releaseExclusiveReadLock(operations, file, exchange);
        }

        deleteLocalWorkFile(exchange);
    }

    public GenericFileExclusiveReadLockStrategy<T> getExclusiveReadLockStrategy() {
        return exclusiveReadLockStrategy;
    }

    public void setExclusiveReadLockStrategy(GenericFileExclusiveReadLockStrategy<T> exclusiveReadLockStrategy) {
        this.exclusiveReadLockStrategy = exclusiveReadLockStrategy;
    }

    private void deleteLocalWorkFile(GenericFileExchange<T> exchange) {
        // delete local work file, if it was used (eg by ftp component)
        File local = exchange.getIn().getHeader(Exchange.FILE_LOCAL_WORK_PATH, File.class);
        if (local != null && local.exists()) {
            if (log.isTraceEnabled()) {
                log.trace("Deleting lock work file: " + local);
            }
            local.delete();
        }
    }
}

