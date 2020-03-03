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
package org.apache.camel.component.file;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;

/**
 * Strategy for acquiring exclusive read locks for files to be consumed. After
 * granting the read lock it is released, we just want to make sure that when we
 * start consuming the file its not currently in progress of being written by
 * third party.
 * <p/>
 * Camel supports out of the box the following strategies:
 * <ul>
 * <li>FileRenameExclusiveReadLockStrategy waiting until its possible to rename
 * the file.</li>
 * <li>FileLockExclusiveReadLockStrategy acquiring a RW file lock for the
 * duration of the processing.</li>
 * <li>MarkerFileExclusiveReadLockStrategy using a marker file for acquiring
 * read lock.</li>
 * <li>FileChangedExclusiveReadLockStrategy using a file changed detection for
 * acquiring read lock.</li>
 * <li>FileIdempotentRepositoryReadLockStrategy using a
 * {@link org.apache.camel.spi.IdempotentRepository} to hold the read locks
 * which allows to support clustering.</li>
 * </ul>
 */
public interface GenericFileExclusiveReadLockStrategy<T> {

    /**
     * Allows custom logic to be run on startup preparing the strategy, such as
     * removing old lock files etc.
     *
     * @param operations generic file operations
     * @param endpoint the endpoint
     * @throws Exception can be thrown in case of errors
     */
    void prepareOnStartup(GenericFileOperations<T> operations, GenericFileEndpoint<T> endpoint) throws Exception;

    /**
     * Acquires exclusive read lock to the file.
     *
     * @param operations generic file operations
     * @param file the file
     * @param exchange the exchange
     * @return <tt>true</tt> if read lock was acquired. If <tt>false</tt> Camel
     *         will skip the file and try it on the next poll
     * @throws Exception can be thrown in case of errors
     */
    boolean acquireExclusiveReadLock(GenericFileOperations<T> operations, GenericFile<T> file, Exchange exchange) throws Exception;

    /**
     * Releases the exclusive read lock granted by the
     * <tt>acquireExclusiveReadLock</tt> method due an abort operation
     * (acquireExclusiveReadLock returned false).
     *
     * @param operations generic file operations
     * @param file the file
     * @param exchange the exchange
     * @throws Exception can be thrown in case of errors
     */
    void releaseExclusiveReadLockOnAbort(GenericFileOperations<T> operations, GenericFile<T> file, Exchange exchange) throws Exception;

    /**
     * Releases the exclusive read lock granted by the
     * <tt>acquireExclusiveReadLock</tt> method due a rollback operation
     * (Exchange processing failed)
     *
     * @param operations generic file operations
     * @param file the file
     * @param exchange the exchange
     * @throws Exception can be thrown in case of errors
     */
    void releaseExclusiveReadLockOnRollback(GenericFileOperations<T> operations, GenericFile<T> file, Exchange exchange) throws Exception;

    /**
     * Releases the exclusive read lock granted by the
     * <tt>acquireExclusiveReadLock</tt> method due a commit operation (Exchange
     * processing succeeded)
     *
     * @param operations generic file operations
     * @param file the file
     * @param exchange the exchange
     * @throws Exception can be thrown in case of errors
     */
    void releaseExclusiveReadLockOnCommit(GenericFileOperations<T> operations, GenericFile<T> file, Exchange exchange) throws Exception;

    /**
     * Sets an optional timeout period.
     * <p/>
     * If the readlock could not be granted within the time period then the wait
     * is stopped and the <tt>acquireExclusiveReadLock</tt> method returns
     * <tt>false</tt>.
     *
     * @param timeout period in millis
     */
    void setTimeout(long timeout);

    /**
     * Sets the check interval period.
     * <p/>
     * The check interval is used for sleeping between attempts to acquire read
     * lock. Setting a high value allows to cater for <i>slow writes</i> in case
     * the producer of the file is slow.
     * <p/>
     * The default period is 1000 millis.
     *
     * @param checkInterval interval in millis
     */
    void setCheckInterval(long checkInterval);

    /**
     * Sets logging level used when a read lock could not be acquired.
     * <p/>
     * Logging level used when a read lock could not be acquired.
     * <p/>
     * The default logging level is WARN
     * 
     * @param readLockLoggingLevel LoggingLevel
     */
    void setReadLockLoggingLevel(LoggingLevel readLockLoggingLevel);

    /**
     * Sets whether marker file should be used or not.
     *
     * @param markerFile <tt>true</tt> to use marker files.
     */
    void setMarkerFiler(boolean markerFile);

    /**
     * Sets whether orphan marker files should be deleted upon startup
     *
     * @param deleteOrphanLockFiles <tt>true</tt> to delete files,
     *            <tt>false</tt> to skip this check
     */
    void setDeleteOrphanLockFiles(boolean deleteOrphanLockFiles);

}
