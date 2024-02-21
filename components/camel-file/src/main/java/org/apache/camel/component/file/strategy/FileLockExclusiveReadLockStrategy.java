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

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.Channel;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileEndpoint;
import org.apache.camel.component.file.GenericFileOperations;
import org.apache.camel.spi.CamelLogger;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.file.GenericFileHelper.asExclusiveReadLockKey;

/**
 * Acquires exclusive read lock to the given file. Will wait until the lock is granted. After granting the read lock it
 * is released, we just want to make sure that when we start consuming the file its not currently in progress of being
 * written by third party.
 */
public class FileLockExclusiveReadLockStrategy extends MarkerFileExclusiveReadLockStrategy {
    private static final Logger LOG = LoggerFactory.getLogger(FileLockExclusiveReadLockStrategy.class);
    private long timeout;
    private long checkInterval = 1000;
    private LoggingLevel readLockLoggingLevel = LoggingLevel.DEBUG;

    @Override
    public void prepareOnStartup(GenericFileOperations<File> operations, GenericFileEndpoint<File> endpoint) {
        // noop
    }

    @Override
    public boolean acquireExclusiveReadLock(GenericFileOperations<File> operations, GenericFile<File> file, Exchange exchange)
            throws Exception {
        // must call super
        if (!super.acquireExclusiveReadLock(operations, file, exchange)) {
            return false;
        }

        File target = new File(file.getAbsoluteFilePath());

        LOG.trace("Waiting for exclusive read lock to file: {}", target);

        FileChannel channel = null;
        RandomAccessFile randomAccessFile = null;

        boolean exclusive = false;
        FileLock lock = null;

        try {
            randomAccessFile = new RandomAccessFile(target, "rw");
            // try to acquire rw lock on the file before we can consume it
            channel = randomAccessFile.getChannel();

            StopWatch watch = new StopWatch();

            while (!exclusive) {
                // timeout check
                if (timeout > 0) {
                    long delta = watch.taken();
                    if (delta > timeout) {
                        CamelLogger.log(LOG, readLockLoggingLevel,
                                "Cannot acquire read lock within " + timeout + " millis. Will skip the file: " + target);
                        // we could not get the lock within the timeout period,
                        // so return false
                        return false;
                    }
                }

                if (!target.exists()) {
                    CamelLogger.log(LOG, readLockLoggingLevel,
                            "Cannot acquire read lock as file no longer exists. Will skip the file: " + file);
                    return false;
                }

                // get the lock using either try lock or not depending on if we
                // are using timeout or not
                try {
                    lock = timeout > 0 ? channel.tryLock() : channel.lock();
                } catch (IllegalStateException ex) {
                    // Also catch the OverlappingFileLockException here. Do
                    // nothing here
                }
                if (lock != null) {
                    LOG.trace("Acquired exclusive read lock: {} to file: {}", lock, target);
                    exclusive = true;
                } else {
                    boolean interrupted = sleep();
                    if (interrupted) {
                        // we were interrupted while sleeping, we are likely
                        // being shutdown so return false
                        return false;
                    }
                }
            }
        } catch (IOException e) {
            // must handle IOException as some apps on Windows etc. will still
            // somehow hold a lock to a file
            // such as AntiVirus or MS Office that has special locks for it's
            // supported files
            if (timeout == 0) {
                // if not using timeout, then we cant retry, so return false
                return false;
            }
            LOG.debug("Cannot acquire read lock. Will try again.", e);
            boolean interrupted = sleep();
            if (interrupted) {
                // we were interrupted while sleeping, we are likely being
                // shutdown so return false
                return false;
            }
        } finally {
            // close channels if we did not grab the lock
            if (!exclusive) {
                IOHelper.close(channel, "while acquiring exclusive read lock for file: " + target, LOG);
                IOHelper.close(randomAccessFile, "while acquiring exclusive read lock for file: " + target, LOG);

                // and also must release super lock
                super.releaseExclusiveReadLockOnAbort(operations, file, exchange);
            }
        }

        // store read-lock state
        exchange.setProperty(asExclusiveReadLockKey(file, Exchange.FILE_LOCK_EXCLUSIVE_LOCK), lock);
        exchange.setProperty(asExclusiveReadLockKey(file, Exchange.FILE_LOCK_RANDOM_ACCESS_FILE), randomAccessFile);
        exchange.setProperty(asExclusiveReadLockKey(file, Exchange.FILE_LOCK_CHANNEL_FILE), channel);

        // we grabbed the lock
        return true;
    }

    @Override
    protected void doReleaseExclusiveReadLock(GenericFile<File> file, Exchange exchange)
            throws Exception {
        // must call super
        super.doReleaseExclusiveReadLock(file, exchange);

        FileLock lock = exchange.getProperty(asExclusiveReadLockKey(file, Exchange.FILE_LOCK_EXCLUSIVE_LOCK), FileLock.class);
        RandomAccessFile rac
                = exchange.getProperty(asExclusiveReadLockKey(file, Exchange.FILE_LOCK_EXCLUSIVE_LOCK), RandomAccessFile.class);
        Channel channel
                = exchange.getProperty(asExclusiveReadLockKey(file, Exchange.FILE_LOCK_CHANNEL_FILE), FileChannel.class);

        String target = file.getFileName();
        if (lock != null) {
            channel = lock.acquiredBy() != null ? lock.acquiredBy() : channel;
            try (FileLock fileLock = lock) {
                // use try-with-resource to auto-close lock
                fileLock.release();
            } finally {
                // close channel and rac as well
                IOHelper.close(channel, "while releasing exclusive read lock for file: " + target, LOG);
                IOHelper.close(rac, "while releasing exclusive read lock for file: " + target, LOG);
            }
        }
    }

    private boolean sleep() {
        LOG.trace("Exclusive read lock not granted. Sleeping for {} millis.", checkInterval);
        try {
            Thread.sleep(checkInterval);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.debug("Sleep interrupted while waiting for exclusive read lock, so breaking out");
            return true;
        }
    }

    public long getTimeout() {
        return timeout;
    }

    @Override
    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    @Override
    public void setCheckInterval(long checkInterval) {
        this.checkInterval = checkInterval;
    }

    @Override
    public void setReadLockLoggingLevel(LoggingLevel readLockLoggingLevel) {
        this.readLockLoggingLevel = readLockLoggingLevel;
    }

}
