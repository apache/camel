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

import org.apache.camel.Exchange;
import org.apache.camel.component.file.FileComponent;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileEndpoint;
import org.apache.camel.component.file.GenericFileExclusiveReadLockStrategy;
import org.apache.camel.component.file.GenericFileOperations;
import org.apache.camel.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Acquires read lock to the given file using a marker file so other Camel consumers wont acquire the same file.
 * This is the default behavior in Camel 1.x.
 */
public class MarkerFileExclusiveReadLockStrategy implements GenericFileExclusiveReadLockStrategy<File> {
    private static final transient Logger LOG = LoggerFactory.getLogger(MarkerFileExclusiveReadLockStrategy.class);
    private File lock;
    private String lockFileName;

    public void prepareOnStartup(GenericFileOperations<File> operations, GenericFileEndpoint<File> endpoint) {
        String dir = endpoint.getConfiguration().getDirectory();
        File file = new File(dir);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Prepare on startup by deleting orphaned lock files from: " + dir);
        }

        deleteLockFiles(file, endpoint.isRecursive());
    }

    public boolean acquireExclusiveReadLock(GenericFileOperations<File> operations,
                                            GenericFile<File> file, Exchange exchange) throws Exception {
        lockFileName = file.getAbsoluteFilePath() + FileComponent.DEFAULT_LOCK_FILE_POSTFIX;
        LOG.trace("Locking the file: {} using the lock file name: {}", file, lockFileName);

        // create a plain file as marker filer for locking (do not use FileLock)
        lock = new File(lockFileName);
        boolean acquired = lock.createNewFile();
        if (!acquired) {
            lock = null;

        }

        return acquired;
    }

    public void releaseExclusiveReadLock(GenericFileOperations<File> operations,
                                         GenericFile<File> file, Exchange exchange) throws Exception {
        if (lock != null) {
            LOG.trace("Unlocking file: {}", lockFileName);

            boolean deleted = FileUtil.deleteFile(lock);
            LOG.trace("Lock file: {} was deleted: {}", lockFileName, deleted);
        }
    }

    public void setTimeout(long timeout) {
        // noop
    }

    public void setCheckInterval(long checkInterval) {
        // noop
    }

    private static void deleteLockFiles(File dir, boolean recursive) {
        File[] files = dir.listFiles();
        if (files == null || files.length == 0) {
            return;
        }

        for (File file : files) {
            if (file.getName().startsWith(".")) {
                // files starting with dot should be skipped
                continue;
            } else if (file.getName().endsWith(FileComponent.DEFAULT_LOCK_FILE_POSTFIX)) {
                LOG.warn("Deleting orphaned lock file: " + file);
                FileUtil.deleteFile(file);
            } else if (recursive && file.isDirectory()) {
                deleteLockFiles(file, true);
            }
        }
    }

}
