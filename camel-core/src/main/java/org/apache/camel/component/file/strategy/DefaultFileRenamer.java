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

/**
 * Camel default file renamer.
 *
 * @version $Revision$
 */
public class DefaultFileRenamer implements FileRenamer {

    private static final boolean ON_WINDOWS = System.getProperty("os.name").startsWith("Windows");

    private String namePrefix;
    private String namePostfix;

    public DefaultFileRenamer() {
    }

    public DefaultFileRenamer(String namePrefix, String namePostfix) {
        this.namePrefix = namePrefix;
        this.namePostfix = namePostfix;
    }

    public File renameFile(File file) {
        File parent = file.getParentFile();
        String name = renameFileName(file);

        if (ON_WINDOWS && (name.indexOf(":") >= 0 || name.startsWith("//"))) {
            return new File(name);
        }
        return new File(parent, name);
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

    protected String renameFileName(File file) {
        StringBuffer buffer = new StringBuffer();
        if (namePrefix != null) {
            buffer.append(namePrefix);
        }
        buffer.append(file.getName());
        if (namePostfix != null) {
            buffer.append(namePostfix);
        }
        return buffer.toString();
    }
}
