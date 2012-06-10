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

import com.jcraft.jsch.ChannelSftp;
import org.apache.camel.Exchange;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileEndpoint;
import org.apache.camel.component.file.GenericFileExclusiveReadLockStrategy;
import org.apache.camel.component.file.GenericFileOperations;
import org.apache.camel.util.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SftpChangedExclusiveReadLockStrategy implements GenericFileExclusiveReadLockStrategy<ChannelSftp.LsEntry> {
    private static final transient Logger LOG = LoggerFactory.getLogger(SftpChangedExclusiveReadLockStrategy.class);
    private long timeout;
    private long checkInterval = 5000;

    @Override
    public void prepareOnStartup(GenericFileOperations<ChannelSftp.LsEntry> tGenericFileOperations, GenericFileEndpoint<ChannelSftp.LsEntry> tGenericFileEndpoint) throws Exception {
        // noop
    }

    public boolean acquireExclusiveReadLock(GenericFileOperations<ChannelSftp.LsEntry> operations, GenericFile<ChannelSftp.LsEntry> file, Exchange exchange) throws Exception {
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
            List<ChannelSftp.LsEntry> files = operations.listFiles(file.getParent());
            for (ChannelSftp.LsEntry f : files) {
                if (f.getFilename().equals(file.getFileName())) {
                    newLastModified = f.getAttrs().getMTime();
                    newLength = f.getAttrs().getSize();
                }
            }

            LOG.trace("Previous last modified: " + lastModified + ", new last modified: " + newLastModified);
            LOG.trace("Previous length: " + length + ", new length: " + newLength);

            if (newLastModified == lastModified && newLength == length && length != 0) {
                // We consider that zero-length files are files in progress on some FTP servers
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
    public void releaseExclusiveReadLock(GenericFileOperations<ChannelSftp.LsEntry> tGenericFileOperations, GenericFile<ChannelSftp.LsEntry> tGenericFile, Exchange exchange) throws Exception {
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

}