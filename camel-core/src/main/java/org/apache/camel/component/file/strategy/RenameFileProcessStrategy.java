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
import java.io.IOException;

import org.apache.camel.component.file.FileEndpoint;
import org.apache.camel.component.file.FileExchange;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * A strategy to rename a file
 * 
 * @version $Revision$
 */
public class RenameFileProcessStrategy extends FileProcessStrategySupport {
    private static final transient Log LOG = LogFactory.getLog(RenameFileProcessStrategy.class);
    private FileRenamer beginRenamer;
    private FileRenamer commitRenamer;

    public RenameFileProcessStrategy() {
        this(true);
    }

    public RenameFileProcessStrategy(boolean lock) {
        this(lock, ".camel/", "");
    }

    public RenameFileProcessStrategy(boolean lock, String namePrefix, String namePostfix) {
        this(lock, new DefaultFileRenamer(namePrefix, namePostfix), null);
    }

    public RenameFileProcessStrategy(boolean lock, String namePrefix, String namePostfix, String preNamePrefix, String preNamePostfix) {
        this(lock, new DefaultFileRenamer(namePrefix, namePostfix), new DefaultFileRenamer(preNamePrefix, preNamePostfix));
    }

    public RenameFileProcessStrategy(boolean lock, FileRenamer commitRenamer, FileRenamer beginRenamer) {
        super(lock);
        this.commitRenamer = commitRenamer;
        this.beginRenamer = beginRenamer;
    }

    @Override
    public boolean begin(FileEndpoint endpoint, FileExchange exchange, File file) throws Exception {
        boolean answer = super.begin(endpoint, exchange, file);

        if (beginRenamer != null) {
            File newName = beginRenamer.renameFile(exchange, file);
            // deleting any existing files before renaming
            File to = renameFile(file, newName);
            exchange.setFile(to);
        }

        return answer;
    }

    @Override
    public void commit(FileEndpoint endpoint, FileExchange exchange, File file) throws Exception {
        File newName = commitRenamer.renameFile(exchange, file);
        renameFile(file, newName);

        // must commit to release the lock
        super.commit(endpoint, exchange, file);
    }

    private static File renameFile(File from, File to) throws IOException {
        // deleting any existing files before renaming
        if (to.exists()) {
            to.delete();
        }

        // make parent folder if missing
        File parent = to.getParentFile();
        if (!parent.exists()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Creating directory: " + parent);
            }
            boolean mkdir = parent.mkdirs();
            if (!mkdir) {
                throw new IOException("Can not create directory: " + parent);
            }
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Renaming file: " + from + " to: " + to);
        }
        boolean renamed = from.renameTo(to);
        if (!renamed) {
            throw new IOException("Can not rename file from: " + from + " to: " + to);
        }

        return to;
    }

    public FileRenamer getBeginRenamer() {
        return beginRenamer;
    }

    public void setBeginRenamer(FileRenamer beginRenamer) {
        this.beginRenamer = beginRenamer;
    }

    public FileRenamer getCommitRenamer() {
        return commitRenamer;
    }

    public void setCommitRenamer(FileRenamer commitRenamer) {
        this.commitRenamer = commitRenamer;
    }

}