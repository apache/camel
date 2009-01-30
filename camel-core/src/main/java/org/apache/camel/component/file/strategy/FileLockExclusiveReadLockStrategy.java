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
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.file.ExclusiveReadLockStrategy;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Acquires exclusive read lock to the given file. Will wait until the lock is granted.
 * After granting the read lock it is released, we just want to make sure that when we start
 * consuming the file its not currently in progress of being written by third party.
 */
public class FileLockExclusiveReadLockStrategy implements ExclusiveReadLockStrategy {
    private static final transient Log LOG = LogFactory.getLog(FileLockExclusiveReadLockStrategy.class);
    private long timeout;

    public boolean acquireExclusiveReadLock(File file) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Waiting for exclusive read lock to file: " + file);
        }

        FileChannel channel = null;
        try {
            // try to acquire rw lock on the file before we can consume it
            channel = new RandomAccessFile(file, "rw").getChannel();
    
            long start = System.currentTimeMillis();
            boolean exclusive = false;

            while (!exclusive) {
                // timeout check
                if (timeout > 0) {
                    long delta = System.currentTimeMillis() - start;
                    if (delta > timeout) {
                        LOG.debug("Could not acquire read lock within " + timeout + " millis. Will skip the file: " + file);
                        // we could not get the lock within the timeout period, so return false
                        return false;
                    }
                }

                // get the lock using either try lock or not depending on if we are using timeout or not
                FileLock lock = null; 
                try {
                    lock = timeout > 0 ? channel.tryLock() : channel.lock();
                } catch (IllegalStateException ex) {
                    // Also catch the OverlappingFileLockException here
                    // Do nothing here
                }
                if (lock != null) {
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("Acquired exclusive read lock: " + lock + " to file: " + file);
                    }
                    // just release it now we dont want to hold it during the rest of the processing
                    lock.release();
                    exclusive = true;
                } else {
                    sleep();
                }
            }
        } catch (IOException e) {
            // must handle IOException as some apps on Windows etc. will still somehow hold a lock to a file
            // such as AntiVirus or MS Office that has special locks for it's supported files
            if (timeout == 0) {
                // if not using timeout, then we cant retry, so rethrow
                throw new RuntimeCamelException(e);
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug("Cannot acquire read lock. Will try again.", e);
            }
            sleep();
        } finally {
            // must close channel
            ObjectHelper.close(channel, "while acquiring exclusive read lock for file: " + file, LOG);
        }

        return true;
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
     * {@link #acquireExclusiveReadLock(java.io.File)} returns <tt>false</tt>.
     *
     * @param timeout period in millis
     */
    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

}
