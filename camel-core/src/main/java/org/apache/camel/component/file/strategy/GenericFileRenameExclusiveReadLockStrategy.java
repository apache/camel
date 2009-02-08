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

import org.apache.camel.Exchange;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileExclusiveReadLockStrategy;
import org.apache.camel.component.file.GenericFileOperations;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Acquires exclusive read lock to the given file. Will wait until the lock is granted.
 * After granting the read lock it is realeased, we just want to make sure that when we start
 * consuming the file its not currently in progress of being written by third party.
 */
public class GenericFileRenameExclusiveReadLockStrategy<T> implements GenericFileExclusiveReadLockStrategy<T> {
    private static final transient Log LOG = LogFactory.getLog(GenericFileRenameExclusiveReadLockStrategy.class);
    private long timeout;

    public boolean acquireExclusiveReadLock(GenericFileOperations<T> operations, GenericFile<T> file,
                                            Exchange exchange) throws Exception {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Waiting for exclusive read lock to file: " + file);
        }

        // the trick is to try to rename the file, if we can rename then we have exclusive read
        // since its a Generic file we cannot use java.nio to get a RW lock
        String newName = file.getFileName() + ".camelExclusiveReadLock";

        // clone and change the name
        GenericFile<T> newFile = file.clone();
        newFile.changeFileName(newName);

        long start = System.currentTimeMillis();

        boolean exclusive = false;
        while (!exclusive) {
             // timeout check
            if (timeout > 0) {
                long delta = System.currentTimeMillis() - start;
                if (delta > timeout) {
                    LOG.debug("Cannot acquire read lock within " + timeout + " millis. Will skip the file: " + file);
                    // we could not get the lock within the timeout period, so return false
                    return false;
                }
            }
            
            exclusive = operations.renameFile(file.getAbsoluteFileName(), newFile.getAbsoluteFileName());
            if (exclusive) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Acquired exclusive read lock to file: " + file);
                }
                // rename it back so we can read it
                operations.renameFile(newFile.getAbsoluteFileName(), file.getAbsoluteFileName());
            } else {
                sleep();
            }
        }

        return true;
    }

    public void releaseExclusiveReadLock(GenericFileOperations<T> opeations, GenericFile<T> file,
                                         Exchange exchange) throws Exception {
        // noop
    }

    private void sleep() {
        LOG.trace("Exclusive read lock not granted. Sleeping for 1000 millis.");
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            // ignore
        }
    }

    public long getTimeout() {
        return timeout;
    }

    /**
     * Sets an optional timeout period.
     * <p/>
     * If the readlock could not be granted within the timeperiod then the wait is stopped and the
     * <tt>acquireExclusiveReadLock</tt> returns <tt>false</tt>.
     *
     * @param timeout period in millis
     */
    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }
}
