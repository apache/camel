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
package org.apache.camel.component.file.remote.strategy;

import java.util.Date;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileEndpoint;
import org.apache.camel.component.file.GenericFileExclusiveReadLockStrategy;
import org.apache.camel.component.file.GenericFileOperations;
import org.apache.camel.spi.CamelLogger;
import org.apache.camel.util.StopWatch;
import org.apache.commons.net.ftp.FTPFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FtpChangedExclusiveReadLockStrategy implements GenericFileExclusiveReadLockStrategy<FTPFile> {
    private static final Logger LOG = LoggerFactory.getLogger(FtpChangedExclusiveReadLockStrategy.class);
    private long timeout;
    private long checkInterval = 5000;
    private LoggingLevel readLockLoggingLevel = LoggingLevel.WARN;
    private long minLength = 1;
    private long minAge;
    private boolean fastExistsCheck;

    @Override
    public void prepareOnStartup(GenericFileOperations<FTPFile> tGenericFileOperations, GenericFileEndpoint<FTPFile> tGenericFileEndpoint) throws Exception {
        // noop
    }

    @Override
    public boolean acquireExclusiveReadLock(GenericFileOperations<FTPFile> operations, GenericFile<FTPFile> file, Exchange exchange) throws Exception {
        boolean exclusive = false;

        LOG.trace("Waiting for exclusive read lock to file: {}", file);

        long lastModified = Long.MIN_VALUE;
        long length = Long.MIN_VALUE;
        StopWatch watch = new StopWatch();
        long startTime = new Date().getTime();

        while (!exclusive) {
            // timeout check
            if (timeout > 0) {
                long delta = watch.taken();
                if (delta > timeout) {
                    CamelLogger.log(LOG, readLockLoggingLevel, "Cannot acquire read lock within " + timeout + " millis. Will skip the file: " + file);
                    // we could not get the lock within the timeout period, so
                    // return false
                    return false;
                }
            }

            long newLastModified = 0;
            long newLength = 0;

            List<FTPFile> files;
            if (fastExistsCheck) {
                // use the absolute file path to only pickup the file we want to
                // check, this avoids expensive
                // list operations if we have a lot of files in the directory
                String path = file.getAbsoluteFilePath();
                if (path.equals("/") || path.equals("\\")) {
                    // special for root (= home) directory
                    LOG.trace("Using fast exists to update file information in home directory");
                    files = operations.listFiles();
                } else {
                    LOG.trace("Using fast exists to update file information for {}", path);
                    files = operations.listFiles(path);
                }
            } else {
                // fast option not enabled, so list the directory and filter the
                // file name
                String path = file.getParent();
                if (path.equals("/") || path.equals("\\")) {
                    // special for root (= home) directory
                    LOG.trace("Using full directory listing in home directory to update file information. Consider enabling fastExistsCheck option.");
                    files = operations.listFiles();
                } else {
                    LOG.trace("Using full directory listing to update file information for {}. Consider enabling fastExistsCheck option.", path);
                    files = operations.listFiles(path);
                }
            }
            LOG.trace("List files {} found {} files", file.getAbsoluteFilePath(), files.size());
            for (FTPFile f : files) {
                boolean match;
                if (fastExistsCheck) {
                    // uses the absolute file path as well
                    match = f.getName().equals(file.getAbsoluteFilePath()) || f.getName().equals(file.getFileNameOnly());
                } else {
                    match = f.getName().equals(file.getFileNameOnly());
                }
                if (match) {
                    newLength = f.getSize();
                    if (f.getTimestamp() != null) {
                        newLastModified = f.getTimestamp().getTimeInMillis();
                    }
                }
            }

            LOG.trace("Previous last modified: " + lastModified + ", new last modified: " + newLastModified);
            LOG.trace("Previous length: " + length + ", new length: " + newLength);
            long newOlderThan = startTime + watch.taken() - minAge;
            LOG.trace("New older than threshold: {}", newOlderThan);

            if (newLength >= minLength && ((minAge == 0 && newLastModified == lastModified && newLength == length) || (minAge != 0 && newLastModified < newOlderThan))) {
                LOG.trace("Read lock acquired.");
                exclusive = true;
            } else {
                // set new base file change information
                lastModified = newLastModified;
                length = newLength;

                boolean interrupted = sleep();
                if (interrupted) {
                    // we were interrupted while sleeping, we are likely being
                    // shutdown so return false
                    return false;
                }
            }
        }

        return exclusive;
    }

    private boolean sleep() {
        LOG.trace("Exclusive read lock not granted. Sleeping for {} millis.", checkInterval);
        try {
            Thread.sleep(checkInterval);
            return false;
        } catch (InterruptedException e) {
            LOG.debug("Sleep interrupted while waiting for exclusive read lock, so breaking out");
            return true;
        }
    }

    @Override
    public void releaseExclusiveReadLockOnAbort(GenericFileOperations<FTPFile> operations, GenericFile<FTPFile> file, Exchange exchange) throws Exception {
        // noop
    }

    @Override
    public void releaseExclusiveReadLockOnRollback(GenericFileOperations<FTPFile> operations, GenericFile<FTPFile> file, Exchange exchange) throws Exception {
        // noop
    }

    @Override
    public void releaseExclusiveReadLockOnCommit(GenericFileOperations<FTPFile> operations, GenericFile<FTPFile> file, Exchange exchange) throws Exception {
        // noop
    }

    public long getTimeout() {
        return timeout;
    }

    @Override
    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public long getCheckInterval() {
        return checkInterval;
    }

    @Override
    public void setCheckInterval(long checkInterval) {
        this.checkInterval = checkInterval;
    }

    @Override
    public void setReadLockLoggingLevel(LoggingLevel readLockLoggingLevel) {
        this.readLockLoggingLevel = readLockLoggingLevel;
    }

    @Override
    public void setMarkerFiler(boolean markerFiler) {
        // noop - not supported by ftp
    }

    @Override
    public void setDeleteOrphanLockFiles(boolean deleteOrphanLockFiles) {
        // noop - not supported by ftp
    }

    public long getMinLength() {
        return minLength;
    }

    public void setMinLength(long minLength) {
        this.minLength = minLength;
    }

    public long getMinAge() {
        return minAge;
    }

    public void setMinAge(long minAge) {
        this.minAge = minAge;
    }

    public boolean isFastExistsCheck() {
        return fastExistsCheck;
    }

    public void setFastExistsCheck(boolean fastExistsCheck) {
        this.fastExistsCheck = fastExistsCheck;
    }
}
