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

import org.apache.camel.component.file.remote.RemoteFile;
import org.apache.camel.component.file.remote.RemoteFileEndpoint;
import org.apache.camel.component.file.remote.RemoteFileExchange;
import org.apache.camel.component.file.remote.RemoteFileExclusiveReadLockStrategy;
import org.apache.camel.component.file.remote.RemoteFileOperations;
import org.apache.camel.component.file.remote.RemoteFileProcessStrategy;

public abstract class RemoteFileProcessStrategySupport implements RemoteFileProcessStrategy {
    private RemoteFileExclusiveReadLockStrategy exclusiveReadLockStrategy;

    public boolean begin(RemoteFileOperations operations, RemoteFileEndpoint endpoint, RemoteFileExchange exchange, RemoteFile file) throws Exception {
        // is we use excluse read then acquire the exclusive read (waiting until we got it)
        if (exclusiveReadLockStrategy != null) {
            boolean lock = exclusiveReadLockStrategy.acquireExclusiveReadLock(operations, file);
            if (!lock) {
                // do not begin sice we could not get the exclusive read lcok
                return false;
            }
        }

        return true;
    }

    public void commit(RemoteFileOperations operations, RemoteFileEndpoint endpoint, RemoteFileExchange exchange, RemoteFile file) throws Exception {
        // nothing
    }

    public void rollback(RemoteFileOperations operations, RemoteFileEndpoint endpoint, RemoteFileExchange exchange, RemoteFile file) {
        // nothing
    }

    public RemoteFileExclusiveReadLockStrategy getExclusiveReadLockStrategy() {
        return exclusiveReadLockStrategy;
    }

    public void setExclusiveReadLockStrategy(RemoteFileExclusiveReadLockStrategy exclusiveReadLockStrategy) {
        this.exclusiveReadLockStrategy = exclusiveReadLockStrategy;
    }
}

