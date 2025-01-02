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
package org.apache.camel.component.smb2.strategy;

import java.util.Date;

import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileOperations;
import org.apache.camel.component.smb2.Smb2Operations;
import org.apache.camel.util.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SmbExclusiveReadLockCheck {

    private static final Logger LOG = LoggerFactory.getLogger(SmbExclusiveReadLockCheck.class);
    private final long startTime;
    private final long minAge;
    private final long minLength;
    private final StopWatch watch;

    private long lastModified;
    private long length;

    public SmbExclusiveReadLockCheck(long minAge, long minLength) {
        this.startTime = new Date().getTime();
        this.minAge = minAge;
        this.minLength = minLength;
        this.watch = new StopWatch();
        this.lastModified = Long.MIN_VALUE;
        this.length = Long.MIN_VALUE;
    }

    public boolean tryAcquireExclusiveReadLock(
            GenericFileOperations<FileIdBothDirectoryInformation> operations,
            GenericFile<FileIdBothDirectoryInformation> file) {
        long newLastModified = 0;
        long newLength = 0;

        FileIdBothDirectoryInformation[] files = getSmbFiles(operations, file);

        LOG.trace("List files {} found {} files", file.getAbsoluteFilePath(), files.length);
        for (FileIdBothDirectoryInformation f : files) {
            boolean match = f.getFileName().equals(file.getFileNameOnly());
            if (match) {
                newLength = f.getEndOfFile();
                newLastModified = f.getChangeTime().toEpochMillis();
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

    private FileIdBothDirectoryInformation[] getSmbFiles(
            GenericFileOperations<FileIdBothDirectoryInformation> operations,
            GenericFile<FileIdBothDirectoryInformation> file) {

        String path = file.getParent();
        if (operations instanceof Smb2Operations smbOperations) {
            return smbOperations.listFiles(path, file.getFileName());
        }
        return operations.listFiles(path);
    }

    private boolean isReadLockAcquired(
            long lastModified, long length, long newLastModified, long newLength, long newOlderThan) {
        return newLength >= minLength && (minAge == 0 && newLastModified == lastModified && newLength == length
                || minAge != 0 && newLastModified < newOlderThan);
    }
}
