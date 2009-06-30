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

import java.io.IOException;

import org.apache.camel.Exchange;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileEndpoint;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.component.file.GenericFileOperations;

public class GenericFileRenameProcessStrategy<T> extends GenericFileProcessStrategySupport<T> {
    private GenericFileRenamer<T> beginRenamer;
    private GenericFileRenamer<T> commitRenamer;

    public GenericFileRenameProcessStrategy() {
    }

    @Override
    public boolean begin(GenericFileOperations<T> operations, GenericFileEndpoint<T> endpoint, Exchange exchange, GenericFile<T> file) throws Exception {
        // must invoke super
        boolean result = super.begin(operations, endpoint, exchange, file);
        if (!result) {
            return false;
        }

        if (beginRenamer != null) {
            GenericFile<T> newName = beginRenamer.renameFile(exchange, file);
            GenericFile<T> to = renameFile(operations, file, newName);
            if (to != null) {
                to.bindToExchange(exchange);
            }
        }

        return true;
    }

    @Override
    public void commit(GenericFileOperations<T> operations, GenericFileEndpoint<T> endpoint, Exchange exchange, GenericFile<T> file) throws Exception {
        // must invoke super
        super.commit(operations, endpoint, exchange, file);

        if (commitRenamer != null) {
            GenericFile<T> newName = commitRenamer.renameFile(exchange, file);
            renameFile(operations, file, newName);
        }
    }

    private GenericFile<T> renameFile(GenericFileOperations<T> operations, GenericFile<T> from, GenericFile<T> to) throws IOException {
        // deleting any existing files before renaming
        try {
            operations.deleteFile(to.getAbsoluteFilePath());
        } catch (GenericFileOperationFailedException e) {
            // ignore the file does not exists
        }
        
        // make parent folder if missing
        boolean mkdir = operations.buildDirectory(to.getParent(), to.isAbsolute());
        
        if (!mkdir) {
            throw new GenericFileOperationFailedException("Cannot create directory: " + to.getParent() + " (could be because of denied permissions)");
        }

        if (log.isDebugEnabled()) {
            log.debug("Renaming file: " + from + " to: " + to);
        }
        boolean renamed = operations.renameFile(from.getAbsoluteFilePath(), to.getAbsoluteFilePath());
        if (!renamed) {
            throw new GenericFileOperationFailedException("Cannot rename file: " + from + " to: " + to);
        }

        return to;
    }

    public GenericFileRenamer<T> getBeginRenamer() {
        return beginRenamer;
    }

    public void setBeginRenamer(GenericFileRenamer<T> beginRenamer) {
        this.beginRenamer = beginRenamer;
    }

    public GenericFileRenamer<T> getCommitRenamer() {
        return commitRenamer;
    }

    public void setCommitRenamer(GenericFileRenamer<T> commitRenamer) {
        this.commitRenamer = commitRenamer;
    }

}
