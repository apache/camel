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
import org.apache.camel.component.file.FileComponent;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileExclusiveReadLockStrategy;
import org.apache.camel.component.file.GenericFileOperations;
import org.apache.camel.util.ExchangeHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Acquires read lock to the given file using a marker file so other Camel consumers wont acquire the same file.
 * This is the default behavior in Camel 1.x.
 */
public class MarkerFileExclusiveReadLockStrategy implements GenericFileExclusiveReadLockStrategy<File> {
    private static final transient Log LOG = LogFactory.getLog(MarkerFileExclusiveReadLockStrategy.class);

    @SuppressWarnings("unchecked")
    public boolean acquireExclusiveReadLock(GenericFileOperations<File> fileGenericFileOperations,
                                            GenericFile<File> file, Exchange exchange) throws Exception {

        String lockFileName = file.getAbsoluteFilePath() + FileComponent.DEFAULT_LOCK_FILE_POSTFIX;
        if (LOG.isTraceEnabled()) {
            LOG.trace("Locking the file: " + file + " using the lock file name: " + lockFileName);
        }

        // create a plain file as marker filer for locking (do not use FileLock)
        File lock = new File(lockFileName);
        boolean acquired = lock.createNewFile();
        if (acquired) {
            exchange.setProperty("CamelFileLock", lock);
            exchange.setProperty("CamelFileLockName", lockFileName);
        }

        return acquired;
    }

    public void releaseExclusiveReadLock(GenericFileOperations<File> operations,
                                         GenericFile<File> file, Exchange exchange) throws Exception {

        File lock = ExchangeHelper.getMandatoryProperty(exchange, "CamelFileLock", File.class);
        String lockFileName = ExchangeHelper.getMandatoryProperty(exchange, "CamelFileLockName", String.class);

        if (LOG.isTraceEnabled()) {
            LOG.trace("Unlocking file: " + lockFileName);
        }

        boolean deleted = lock.delete();
        if (LOG.isTraceEnabled()) {
            LOG.trace("Lock file: " + lockFileName + " was deleted: " + deleted);
        }
    }

    public void setTimeout(long timeout) {
        // noop
    }

}
