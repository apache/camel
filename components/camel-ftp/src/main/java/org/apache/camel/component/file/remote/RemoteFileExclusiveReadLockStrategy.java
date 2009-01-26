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
package org.apache.camel.component.file.remote;

/**
 * Strategy for acquiring exclusive read locks for files to be consumed.
 * After granting the read lock it is realeased, we just want to make sure that when we start
 * consuming the file its not currently in progress of being written by third party.
 * <p/>
 * Camel supports out of the box the following strategies:
 * <ul>
 * <li>RemoteFileRenameExclusiveReadLockStrategy waiting until its possible to rename the file.</li>
 * </ul>
 */
public interface RemoteFileExclusiveReadLockStrategy {

    /**
     * Acquires exclusive read lock to the file.
     *
     * @param operations remote file operations
     * @param file       the remote file
     * @return <tt>true</tt> if read lock was acquired. If <tt>false</tt> Camel will skip the file and
     *         try it on the next poll
     */
    boolean acquireExclusiveReadLock(RemoteFileOperations operations, RemoteFile file);
}
