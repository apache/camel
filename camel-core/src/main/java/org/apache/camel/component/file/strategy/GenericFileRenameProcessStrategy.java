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

import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileEndpoint;
import org.apache.camel.component.file.GenericFileExchange;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.component.file.GenericFileOperations;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class GenericFileRenameProcessStrategy extends GenericFileProcessStrategySupport {
    private static final transient Log LOG = LogFactory.getLog(org.apache.camel.component.file.strategy.GenericFileRenameProcessStrategy.class);
    private GenericFileRenamer beginRenamer;
    private GenericFileRenamer commitRenamer;

    public GenericFileRenameProcessStrategy() {
    }

    public GenericFileRenameProcessStrategy(String namePrefix, String namePostfix) {
        this(new GenericFileDefaultRenamer(namePrefix, namePostfix), null);
    }

    public GenericFileRenameProcessStrategy(String namePrefix, String namePostfix, String preNamePrefix, String preNamePostfix) {
        this(new GenericFileDefaultRenamer(namePrefix, namePostfix), new GenericFileDefaultRenamer(preNamePrefix, preNamePostfix));
    }

    public GenericFileRenameProcessStrategy(GenericFileRenamer commitRenamer, GenericFileRenamer beginRenamer) {
        this.commitRenamer = commitRenamer;
        this.beginRenamer = beginRenamer;
    }

    @Override
    public boolean begin(GenericFileOperations operations, GenericFileEndpoint endpoint, GenericFileExchange exchange, GenericFile file) throws Exception {
        if (beginRenamer != null) {
            GenericFile newName = beginRenamer.renameFile(exchange, file);
            GenericFile to = renameFile(operations, file, newName);
            exchange.setGenericFile(to);
        }

        return true;
    }

    @Override
    public void commit(GenericFileOperations operations, GenericFileEndpoint endpoint, GenericFileExchange exchange, GenericFile file) throws Exception {
        if (commitRenamer != null) {
            GenericFile newName = commitRenamer.renameFile(exchange, file);
            renameFile(operations, file, newName);
        }
    }

    private static GenericFile renameFile(GenericFileOperations operations, GenericFile from, GenericFile to) throws IOException {
        // deleting any existing files before renaming
        try {
            operations.deleteFile(to.getAbsoluteFileName());
        } catch (GenericFileOperationFailedException e) {
            // ignore the file does not exists
        }

        // make parent folder if missing
        String parent = to.getParent();
        boolean mkdir = operations.buildDirectory(parent);
        if (!mkdir) {
            throw new GenericFileOperationFailedException("Cannot create directory: " + parent + " (could be because of denied permissions)");
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Renaming file: " + from + " to: " + to);
        }
        boolean renamed = operations.renameFile(from.getAbsoluteFileName(), to.getAbsoluteFileName());
        if (!renamed) {
            throw new GenericFileOperationFailedException("Cannot rename file: " + from + " to: " + to);
        }

        return to;
    }

    public GenericFileRenamer getBeginRenamer() {
        return beginRenamer;
    }

    public void setBeginRenamer(GenericFileRenamer beginRenamer) {
        this.beginRenamer = beginRenamer;
    }

    public GenericFileRenamer getCommitRenamer() {
        return commitRenamer;
    }

    public void setCommitRenamer(GenericFileRenamer commitRenamer) {
        this.commitRenamer = commitRenamer;
    }

}
