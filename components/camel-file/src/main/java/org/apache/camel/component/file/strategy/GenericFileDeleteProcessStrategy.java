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
package org.apache.camel.component.file.strategy;

import org.apache.camel.Exchange;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileEndpoint;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.component.file.GenericFileOperations;
import org.apache.camel.support.ExchangeHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GenericFileDeleteProcessStrategy<T> extends GenericFileProcessStrategySupport<T> {

    private static final Logger LOG = LoggerFactory.getLogger(GenericFileDeleteProcessStrategy.class);

    private GenericFileRenamer<T> failureRenamer;
    private GenericFileRenamer<T> beginRenamer;

    @Override
    public boolean begin(GenericFileOperations<T> operations, GenericFileEndpoint<T> endpoint, Exchange exchange, GenericFile<T> file) throws Exception {

        // must invoke super
        boolean result = super.begin(operations, endpoint, exchange, file);
        if (!result) {
            return false;
        }

        // okay we got the file then execute the begin renamer
        if (beginRenamer != null) {
            GenericFile<T> newName = beginRenamer.renameFile(exchange, file);
            GenericFile<T> to = renameFile(operations, file, newName);
            if (to != null) {
                to.bindToExchange(exchange);
            }
        }

        return true;
    }

    @Override
    public void commit(GenericFileOperations<T> operations, GenericFileEndpoint<T> endpoint, Exchange exchange, GenericFile<T> file) throws Exception {

        // special for file lock strategy as we must release that lock first
        // before we can delete the file
        boolean releaseEager = exclusiveReadLockStrategy instanceof FileLockExclusiveReadLockStrategy;

        if (releaseEager) {
            exclusiveReadLockStrategy.releaseExclusiveReadLockOnCommit(operations, file, exchange);
        }

        try {
            deleteLocalWorkFile(exchange);
            operations.releaseRetrievedFileResources(exchange);

            int retries = 3;
            boolean deleted = false;

            while (retries > 0 && !deleted) {
                retries--;

                if (operations.deleteFile(file.getAbsoluteFilePath())) {
                    // file is deleted
                    deleted = true;
                    break;
                }

                // some OS can report false when deleting but the file is still
                // deleted
                // use exists to check instead
                boolean exits = operations.existsFile(file.getAbsoluteFilePath());
                if (!exits) {
                    deleted = true;
                } else {
                    LOG.trace("File was not deleted at this attempt will try again in 1 sec.: {}", file);
                    // sleep a bit and try again
                    Thread.sleep(1000);
                }
            }
            if (!deleted) {
                throw new GenericFileOperationFailedException("Cannot delete file: " + file);
            }
        } finally {
            // must release lock last
            if (!releaseEager && exclusiveReadLockStrategy != null) {
                exclusiveReadLockStrategy.releaseExclusiveReadLockOnCommit(operations, file, exchange);
            }
        }
    }

    @Override
    public void rollback(GenericFileOperations<T> operations, GenericFileEndpoint<T> endpoint, Exchange exchange, GenericFile<T> file) throws Exception {
        try {
            deleteLocalWorkFile(exchange);
            operations.releaseRetrievedFileResources(exchange);

            // moved the failed file if specifying the moveFailed option
            if (failureRenamer != null) {
                // create a copy and bind the file to the exchange to be used by
                // the renamer to evaluate the file name
                Exchange copy = ExchangeHelper.createCopy(exchange, true);
                file.bindToExchange(copy);
                // must preserve message id
                copy.getIn().setMessageId(exchange.getIn().getMessageId());
                copy.setExchangeId(exchange.getExchangeId());

                GenericFile<T> newName = failureRenamer.renameFile(copy, file);
                renameFile(operations, file, newName);
            }
        } finally {
            // must release lock last
            if (exclusiveReadLockStrategy != null) {
                exclusiveReadLockStrategy.releaseExclusiveReadLockOnRollback(operations, file, exchange);
            }
        }
    }

    public GenericFileRenamer<T> getFailureRenamer() {
        return failureRenamer;
    }

    public void setFailureRenamer(GenericFileRenamer<T> failureRenamer) {
        this.failureRenamer = failureRenamer;
    }

    public GenericFileRenamer<T> getBeginRenamer() {
        return beginRenamer;
    }

    public void setBeginRenamer(GenericFileRenamer<T> beginRenamer) {
        this.beginRenamer = beginRenamer;
    }
}
