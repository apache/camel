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

import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileOperations;
import org.apache.camel.util.StopWatch;
import org.apache.commons.net.ftp.FTPFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExclusiveReadLockCheck {
    private static final Logger LOG = LoggerFactory.getLogger(ExclusiveReadLockCheck.class);
    private final boolean fastExistsCheck;
    private final long startTime;
    private final long minAge;
    private final long minLength;
    private final StopWatch watch;

    private long lastModified;
    private long length;

    public ExclusiveReadLockCheck(boolean fastExistsCheck, long minAge, long minLength) {
        this.fastExistsCheck = fastExistsCheck;
        this.startTime = new Date().getTime();
        this.minAge = minAge;
        this.minLength = minLength;
        this.watch = new StopWatch();

        this.lastModified = Long.MIN_VALUE;
        this.length = Long.MIN_VALUE;
    }

    public boolean tryAcquireExclusiveReadLock(GenericFileOperations<FTPFile> operations, GenericFile<FTPFile> file) {
        long newLastModified = 0;
        long newLength = 0;

        FTPFile[] files = getFtpFiles(operations, file);

        LOG.trace("List files {} found {} files", file.getAbsoluteFilePath(), files.length);
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

        LOG.trace("Previous last modified: {}, new last modified: {}", lastModified, newLastModified);
        LOG.trace("Previous length: {}, new length: {}", length, newLength);
        long newOlderThan = startTime + watch.taken() - minAge;
        LOG.trace("New older than threshold: {}", newOlderThan);

        if (isReadLockAcquired(lastModified, length, newLastModified, newLength, newOlderThan)) {
            LOG.trace("Read lock acquired.");
            return true;
        }

        lastModified = newLastModified;
        length = newLength;
        return false;
    }

    private FTPFile[] getFtpFiles(GenericFileOperations<FTPFile> operations, GenericFile<FTPFile> file) {
        FTPFile[] files;
        if (fastExistsCheck) {
            // use the absolute file path to only pickup the file we want to
            // check, this avoids expensive
            // list operations if we have a lot of files in the directory
            files = getFtpFilesFast(operations, file);
        } else {
            files = getFtpFilesByFilter(operations, file);
        }
        return files;
    }

    private FTPFile[] getFtpFilesByFilter(GenericFileOperations<FTPFile> operations, GenericFile<FTPFile> file) {
        // fast option not enabled, so list the directory and filter the
        // file name
        String path = file.getParent();
        if (path.equals("/") || path.equals("\\")) {
            // special for root (= home) directory
            LOG.trace(
                    "Using full directory listing in home directory to update file information. Consider enabling fastExistsCheck option.");
            return operations.listFiles();
        } else {
            LOG.trace(
                    "Using full directory listing to update file information for {}. Consider enabling fastExistsCheck option.",
                    path);
            return operations.listFiles(path);
        }
    }

    private FTPFile[] getFtpFilesFast(GenericFileOperations<FTPFile> operations, GenericFile<FTPFile> file) {
        String path = file.getAbsoluteFilePath();
        if (path.equals("/") || path.equals("\\")) {
            // special for root (= home) directory
            LOG.trace("Using fast exists to update file information in home directory");
            return operations.listFiles();
        } else {
            LOG.trace("Using fast exists to update file information for {}", path);
            return operations.listFiles(path);
        }
    }

    private boolean isReadLockAcquired(
            long lastModified, long length, long newLastModified, long newLength, long newOlderThan) {
        return newLength >= minLength && (minAge == 0 && newLastModified == lastModified && newLength == length
                || minAge != 0 && newLastModified < newOlderThan);
    }
}
