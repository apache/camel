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
 * @version $Revision: 1.1 $
 */
public class RenameFileProcessStrategy extends FileProcessStrategySupport {
    private static final transient Log LOG = LogFactory.getLog(RenameFileProcessStrategy.class);
    private FileRenamer renamer;

    public RenameFileProcessStrategy() {
        this(true);
    }

    public RenameFileProcessStrategy(boolean lock) {
        this(lock, ".camel/", "");
    }

    public RenameFileProcessStrategy(boolean lock, String namePrefix, String namePostfix) {
        this(lock, new DefaultFileRenamer(namePrefix, namePostfix));
    }

    public RenameFileProcessStrategy(boolean lock, FileRenamer renamer) {
        super(lock);
        this.renamer = renamer;
    }

    public void commit(FileEndpoint endpoint, FileExchange exchange, File file) throws Exception {
        File newName = renamer.renameFile(file);
        newName.getParentFile().mkdirs();

        if (LOG.isDebugEnabled()) {
            LOG.debug("Renaming file: " + file + " to: " + newName);
        }
        boolean renamed = file.renameTo(newName);
        if (!renamed) {
            throw new IOException("Could not rename file from: " + file + " to " + newName);
        }
        super.commit(endpoint, exchange, file);
    }

    public FileRenamer getRenamer() {
        return renamer;
    }

    public void setRenamer(FileRenamer renamer) {
        this.renamer = renamer;
    }
}