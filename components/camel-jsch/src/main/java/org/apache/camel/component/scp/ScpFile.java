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
package org.apache.camel.component.scp;


/**
 * SFTP remote file operations
 */
public class ScpFile {
    private boolean directory;
    private int attrs;
    private int length;
    private String name;
    private String parent;

    public String header() {
        StringBuilder buffer = new StringBuilder();
        buffer.append(directory ? "D" : "C");
        buffer.append(" ");
        return buffer.toString();
    }

    public boolean isDirectory() {
        return directory;
    }
    public void setDirectory(boolean directory) {
        this.directory = directory;
    }
    public int getAttrs() {
        return attrs;
    }
    public void setAttrs(int attrs) {
        this.attrs = attrs;
    }
    public int getLength() {
        return length;
    }
    public void setLength(int length) {
        this.length = length;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getParent() {
        return parent;
    }

    public void setParent(String parent) {
        this.parent = parent;
    }
}
