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
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileOperations;

/**
 * Acquires exclusive read lock to the given file. Will wait until the lock is granted.
 * After granting the read lock it is released, we just want to make sure that when we start
 * consuming the file its not currently in progress of being written by third party.
 * <p/>
 * This implementation is only supported by the File component, that leverages the {@link MarkerFileExclusiveReadLockStrategy}
 * as well, to ensure only acquiring locks on files, which is not already in progress by another process,
 * that have marked this using the marker file.
 * <p/>
 * Setting the option {@link #setMarkerFiler(boolean)} to <tt>false</tt> allows to turn off using marker files.
 */
public class FileRenameExclusiveReadLockStrategy extends GenericFileRenameExclusiveReadLockStrategy<File> {

    private MarkerFileExclusiveReadLockStrategy marker = new MarkerFileExclusiveReadLockStrategy();
    private boolean markerFile = true;

    @Override
    public boolean acquireExclusiveReadLock(GenericFileOperations<File> operations, GenericFile<File> file, Exchange exchange) throws Exception {
        // must call marker first
        if (markerFile && !marker.acquireExclusiveReadLock(operations, file, exchange)) {
            return false;
        }

        return super.acquireExclusiveReadLock(operations, file, exchange);
    }

    @Override
    public void releaseExclusiveReadLockOnAbort(GenericFileOperations<File> operations, GenericFile<File> file, Exchange exchange) throws Exception {
        // must call marker first
        try {
            if (markerFile) {
                marker.releaseExclusiveReadLockOnAbort(operations, file, exchange);
            }
        } finally {
            super.releaseExclusiveReadLockOnAbort(operations, file, exchange);
        }
    }

    @Override
    public void releaseExclusiveReadLockOnRollback(GenericFileOperations<File> operations, GenericFile<File> file, Exchange exchange) throws Exception {
        // must call marker first
        try {
            if (markerFile) {
                marker.releaseExclusiveReadLockOnRollback(operations, file, exchange);
            }
        } finally {
            super.releaseExclusiveReadLockOnRollback(operations, file, exchange);
        }
    }

    @Override
    public void releaseExclusiveReadLockOnCommit(GenericFileOperations<File> operations, GenericFile<File> file, Exchange exchange) throws Exception {
        // must call marker first
        try {
            if (markerFile) {
                marker.releaseExclusiveReadLockOnCommit(operations, file, exchange);
            }
        } finally {
            super.releaseExclusiveReadLockOnCommit(operations, file, exchange);
        }
    }

    @Override
    public void setMarkerFiler(boolean markerFile) {
        this.markerFile = markerFile;
    }

}
