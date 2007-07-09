/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.file.strategy;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.camel.component.file.FileEndpoint;
import org.apache.camel.component.file.FileExchange;

import java.io.File;
import java.io.IOException;

/**
 * A strategy to rename a file
 * 
 * @version $Revision: 1.1 $
 */
public class RenameFileStrategy extends FileStategySupport {
    private static final transient Log log = LogFactory.getLog(RenameFileStrategy.class);

    private String namePrefix;
    private String namePostfix;

    public RenameFileStrategy() {
        this(true);
    }

    public RenameFileStrategy(boolean lock) {
        this(lock, ".camel/", "");
    }

    public RenameFileStrategy(boolean lock, String namePrefix, String namePostfix) {
        super(lock);
        if (namePrefix != null) {
            this.namePrefix = namePrefix;
        }
        if (namePostfix != null) {
            this.namePostfix = namePostfix;
        }
    }

    public void commit(FileEndpoint endpoint, FileExchange exchange, File file) throws Exception {
        File parent = file.getParentFile();
        String name = getNamePrefix() + file.getName() + getNamePostfix();
        File newName = new File(parent, name);
        newName.getParentFile().mkdirs();

        if (log.isDebugEnabled()) {
            log.debug("Renaming file: " + file + " to: " + newName);
        }
        boolean renamed = file.renameTo(newName);
        if (!renamed) {
            throw new IOException("Could not rename file from: " + file + " to " + newName);
        }
        super.commit(endpoint, exchange, file);
    }

    public String getNamePostfix() {
        return namePostfix;
    }

    /**
     * Sets the name postfix appended to moved files. For example
     * to rename all the files from * to *.done set this value to ".done"
     */
    public void setNamePostfix(String namePostfix) {
        this.namePostfix = namePostfix;
    }

    public String getNamePrefix() {
        return namePrefix;
    }

    /**
     * Sets the name prefix appended to moved files. For example
     * to move processed files into a hidden directory called ".camel"
     * set this value to ".camel/"
     */
    public void setNamePrefix(String namePrefix) {
        this.namePrefix = namePrefix;
    }
}