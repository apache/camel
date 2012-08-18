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
package org.apache.camel.component.file.remote.strategy;

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileEndpoint;
import org.apache.camel.component.file.GenericFileExclusiveReadLockStrategy;
import org.apache.camel.component.file.GenericFileOperations;
import org.apache.camel.util.StopWatch;
import org.apache.commons.net.ftp.FTPFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FtpChangedExclusiveReadLockStrategy implements GenericFileExclusiveReadLockStrategy<FTPFile> {
    private static final transient Logger LOG = LoggerFactory.getLogger(FtpChangedExclusiveReadLockStrategy.class);
    private long timeout;
    private long checkInterval = 5000;
    private long minLength = 1;
    private boolean fastExistsCheck;

    @Override
    public void prepareOnStartup(GenericFileOperations<FTPFile> tGenericFileOperations, GenericFileEndpoint<FTPFile> tGenericFileEndpoint) throws Exception {
        // noop
    }

    public boolean acquireExclusiveReadLock(GenericFileOperations<FTPFile> operations, GenericFile<FTPFile> file, Exchange exchange) throws Exception {
        boolean exclusive = false;

        LOG.trace("Waiting for exclusive read lock to file: " + file);

        long lastModified = Long.MIN_VALUE;
        long length = Long.MIN_VALUE;
        StopWatch watch = new StopWatch();

        while (!exclusive) {
            // timeout check
            if (timeout > 0) {
                long delta = watch.taken();
                if (delta > timeout) {
                    LOG.warn("Cannot acquire read lock within " + timeout + " millis. Will skip the file: " + file);
                    // we could not get the lock within the timeout period, so return false
                    return false;
                }
            }

            long newLastModified = 0;
            long newLength = 0;
            List<FTPFile> files;
            if (fastExistsCheck) {
                // use the absolute file path to only pickup the file we want to check, this avoids expensive
                // list operations if we have a lot of files in the directory
                LOG.trace("Using fast exists to update file information for {}", file);
                files = operations.listFiles(file.getAbsoluteFilePath());
            } else {
                LOG.trace("Using full directory listing to update file information for {}. Consider enabling fastExistsCheck option.", file);
                // fast option not enabled, so list the directory and filter the file name
                files = operations.listFiles(file.getParent());
            }
            LOG.trace("List files {} found {} files", file.getAbsoluteFilePath(), files.size());
            for (FTPFile f : files) {
                if (f.getName().equals(file.getFileName())) {
                    newLastModified = f.getTimestamp().getTimeInMillis();
                    newLength = f.getSize();
                }
            }

            LOG.trace("Previous last modified: " + lastModified + ", new last modified: " + newLastModified);
            LOG.trace("Previous length: " + length + ", new length: " + newLength);

            if (length >= minLength && (newLastModified == lastModified && newLength == length)) {
                LOG.trace("Read lock acquired.");
                exclusive = true;
            } else {
                // set new base file change information
                lastModified = newLastModified;
                length = newLength;

                boolean interrupted = sleep();
                if (interrupted) {
                    // we were interrupted while sleeping, we are likely being shutdown so return false
                    return false;
                }
            }
        }

        return exclusive;
    }

    private boolean sleep() {
        LOG.trace("Exclusive read lock not granted. Sleeping for " + checkInterval + " millis.");
        try {
            Thread.sleep(checkInterval);
            return false;
        } catch (InterruptedException e) {
            LOG.debug("Sleep interrupted while waiting for exclusive read lock, so breaking out");
            return true;
        }
    }

    @Override
    public void releaseExclusiveReadLock(GenericFileOperations<FTPFile> tGenericFileOperations, GenericFile<FTPFile> tGenericFile, Exchange exchange) throws Exception {
        // noop
    }

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public long getCheckInterval() {
        return checkInterval;
    }

    public void setCheckInterval(long checkInterval) {
        this.checkInterval = checkInterval;
    }

    public long getMinLength() {
        return minLength;
    }

    public void setMinLength(long minLength) {
        this.minLength = minLength;
    }

    public boolean isFastExistsCheck() {
        return fastExistsCheck;
    }

    public void setFastExistsCheck(boolean fastExistsCheck) {
        this.fastExistsCheck = fastExistsCheck;
    }
}