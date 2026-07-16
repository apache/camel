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

import com.jcraft.jsch.ChannelSftp;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileOperations;
import org.apache.camel.component.file.remote.SftpRemoteFile;
import org.apache.camel.util.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SftpExclusiveReadLockCheck {
    private static final Logger LOG = LoggerFactory.getLogger(SftpExclusiveReadLockCheck.class);
    private final boolean fastExistsCheck;
    private final long startTime;
    private final long minAge;
    private final long minLength;
    private final StopWatch watch;

    private long lastModified;
    private long length;

    public SftpExclusiveReadLockCheck(boolean fastExistsCheck, long minAge, long minLength) {
        this.fastExistsCheck = fastExistsCheck;
        this.startTime = System.currentTimeMillis();
        this.minAge = minAge;
        this.minLength = minLength;
        this.watch = new StopWatch();

        this.lastModified = Long.MIN_VALUE;
        this.length = Long.MIN_VALUE;
    }

    public boolean tryAcquireExclusiveReadLock(
            GenericFileOperations<ChannelSftp.LsEntry> operations, GenericFile<ChannelSftp.LsEntry> file) {
        long newLastModified = 0;
        long newLength = 0;

        Object[] files = listFiles(operations, file);

        LOG.trace("List files {} found {} files", file.getAbsoluteFilePath(), files.length);
        for (Object f : files) {
            SftpRemoteFile rf = (SftpRemoteFile) f;
            boolean match;
            if (fastExistsCheck) {
                match = rf.getFilename().equals(file.getAbsoluteFilePath())
                        || rf.getFilename().equals(file.getFileNameOnly());
            } else {
                match = rf.getFilename().equals(file.getFileNameOnly());
            }
            if (match) {
                newLastModified = rf.getLastModified();
                newLength = rf.getFileLength();
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

    private Object[] listFiles(
            GenericFileOperations<ChannelSftp.LsEntry> operations, GenericFile<ChannelSftp.LsEntry> file) {
        if (fastExistsCheck) {
            return listFilesFast(operations, file);
        } else {
            return listFilesByFilter(operations, file);
        }
    }

    private Object[] listFilesByFilter(
            GenericFileOperations<ChannelSftp.LsEntry> operations, GenericFile<ChannelSftp.LsEntry> file) {
        String path = file.getParent();
        if (path.equals("/") || path.equals("\\")) {
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

    private Object[] listFilesFast(
            GenericFileOperations<ChannelSftp.LsEntry> operations, GenericFile<ChannelSftp.LsEntry> file) {
        String path = file.getAbsoluteFilePath();
        if (path.equals("/") || path.equals("\\")) {
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
