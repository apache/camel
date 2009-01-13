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

import java.io.IOException;

import org.apache.camel.component.file.remote.RemoteFile;
import org.apache.camel.component.file.remote.RemoteFileEndpoint;
import org.apache.camel.component.file.remote.RemoteFileExchange;
import org.apache.camel.component.file.remote.RemoteFileOperationFailedException;
import org.apache.camel.component.file.remote.RemoteFileOperations;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class RenameRemoteFileProcessStrategy extends RemoteFileProcessStrategySupport {
    private static final transient Log LOG = LogFactory.getLog(RenameRemoteFileProcessStrategy.class);
    private RemoteFileRenamer beginRenamer;
    private RemoteFileRenamer commitRenamer;

    public RenameRemoteFileProcessStrategy() {
    }

    public RenameRemoteFileProcessStrategy(String namePrefix, String namePostfix) {
        this(new DefaultRemoteFileRenamer(namePrefix, namePostfix), null);
    }

    public RenameRemoteFileProcessStrategy(String namePrefix, String namePostfix, String preNamePrefix, String preNamePostfix) {
        this(new DefaultRemoteFileRenamer(namePrefix, namePostfix), new DefaultRemoteFileRenamer(preNamePrefix, preNamePostfix));
    }

    public RenameRemoteFileProcessStrategy(RemoteFileRenamer commitRenamer, RemoteFileRenamer beginRenamer) {
        this.commitRenamer = commitRenamer;
        this.beginRenamer = beginRenamer;
    }

    @Override
    public boolean begin(RemoteFileOperations operations, RemoteFileEndpoint endpoint, RemoteFileExchange exchange, RemoteFile file) throws Exception {
        if (beginRenamer != null) {
            RemoteFile newName = beginRenamer.renameFile(exchange, file);
            RemoteFile to = renameFile(operations, file, newName);
            exchange.setRemoteFile(to);
        }

        return true;
    }

    @Override
    public void commit(RemoteFileOperations operations, RemoteFileEndpoint endpoint, RemoteFileExchange exchange, RemoteFile file) throws Exception {
        if (commitRenamer != null) {
            RemoteFile newName = commitRenamer.renameFile(exchange, file);
            renameFile(operations, file, newName);
        }
    }

    private static RemoteFile renameFile(RemoteFileOperations operations, RemoteFile from, RemoteFile to) throws IOException {
        // deleting any existing files before renaming
        boolean deleted = false;
        try {
            deleted = operations.deleteFile(to.getAbsolutelFileName());
        } catch (RemoteFileOperationFailedException e) {
            // ignore the file does not exists
        }

        if (!deleted) {
            // if we could not delete any existing file then maybe the folder is missing
            // build folder if needed
            String name = to.getAbsolutelFileName();
            int lastPathIndex = name.lastIndexOf('/');
            if (lastPathIndex != -1) {
                String directory = name.substring(0, lastPathIndex);
                if (!operations.buildDirectory(directory)) {
                    LOG.warn("Cannot build directory: " + directory + " (maybe because of denied permissions)");
                }
            }
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Renaming file: " + from + " to: " + to);
        }
        boolean renamed = operations.renameFile(from.getAbsolutelFileName(), to.getAbsolutelFileName());
        if (!renamed) {
            throw new RemoteFileOperationFailedException("Cannot rename file: " + from + " to: " + to);
        }

        return to;
    }

    public RemoteFileRenamer getBeginRenamer() {
        return beginRenamer;
    }

    public void setBeginRenamer(RemoteFileRenamer beginRenamer) {
        this.beginRenamer = beginRenamer;
    }

    public RemoteFileRenamer getCommitRenamer() {
        return commitRenamer;
    }

    public void setCommitRenamer(RemoteFileRenamer commitRenamer) {
        this.commitRenamer = commitRenamer;
    }

}
