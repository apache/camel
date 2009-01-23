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
package org.apache.camel.component.file;

import java.net.URI;

public class GenericFileConfiguration {

    private String file;
    private boolean directory = true;

    public GenericFileConfiguration() {
        super();
    }

    public String toString() {
        return "/" + file;
    }

    public void configure(URI uri) {
        setFile(uri.getPath());
    }

    public String getFile() {
        return file;
    }

    public void setFile(String file) {
        // Avoid accidentally putting everything in root on
        // servers that expose the full filesystem
        if (file.startsWith("/")) {
            file = file.substring(1);
        }
        this.file = file;
    }

    public boolean isDirectory() {
        return directory;
    }

    public void setDirectory(boolean directory) {
        this.directory = directory;
    }

}
