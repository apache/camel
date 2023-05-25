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

package org.apache.camel.component.file.azure.strategy;

import java.util.Date;

import com.azure.storage.file.share.models.ShareFileItem;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileOperations;
import org.apache.camel.util.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FilesExclusiveReadLockCheck {
    // based on misnamed ExclusiveReadLockCheck, in reality it's FTP specific
    private static final Logger LOG = LoggerFactory.getLogger(FilesExclusiveReadLockCheck.class);
    private final boolean fastExistsCheck;
    private final long startTime;
    private final long minAge;
    private final long minLength;
    private final StopWatch watch;

    private long lastModified;
    private long length;

    public FilesExclusiveReadLockCheck(boolean fastExistsCheck, long minAge, long minLength) {
        this.fastExistsCheck = fastExistsCheck;
        this.startTime = new Date().getTime();
        this.minAge = minAge;
        this.minLength = minLength;
        this.watch = new StopWatch();

        this.lastModified = Long.MIN_VALUE;
        this.length = Long.MIN_VALUE;
    }

    public boolean tryAcquireExclusiveReadLock(
            GenericFileOperations<ShareFileItem> operations, GenericFile<ShareFileItem> file) {
        long newLastModified = 0;
        long newLength = 0;

        ShareFileItem[] files = getFiles(operations, file);

        LOG.trace("List files {} found {} files", file.getAbsoluteFilePath(), files.length);
        for (ShareFileItem f : files) {
            boolean match;
            if (fastExistsCheck) {
                // uses the absolute file path as well
                match = f.getName().equals(file.getAbsoluteFilePath()) || f.getName().equals(file.getFileNameOnly());
            } else {
                match = f.getName().equals(file.getFileNameOnly());
            }
            if (match) {
                newLength = f.getFileSize();
                newLastModified = lastModified(f);
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

    private ShareFileItem[] getFiles(GenericFileOperations<ShareFileItem> operations, GenericFile<ShareFileItem> file) {
        ShareFileItem[] files;
        if (fastExistsCheck) {
            // use the absolute file path to only pickup the file we want to
            // check, this avoids expensive
            // list operations if we have a lot of files in the directory
            files = getFilesFast(operations, file);
        } else {
            files = getFilesByFilter(operations, file);
        }
        return files;
    }

    private ShareFileItem[] getFilesByFilter(GenericFileOperations<ShareFileItem> operations, GenericFile<ShareFileItem> file) {
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

    private ShareFileItem[] getFilesFast(GenericFileOperations<ShareFileItem> operations, GenericFile<ShareFileItem> file) {
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

    private static long lastModified(ShareFileItem file) {
        return file.getProperties().getLastModified().toInstant().toEpochMilli();
    }
}
