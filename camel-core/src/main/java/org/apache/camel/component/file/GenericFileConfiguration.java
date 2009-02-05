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
import org.apache.camel.util.FileUtil;

public class GenericFileConfiguration {

    private String file;

    public void configure(URI uri) {
        setFile(uri.getPath());
    }

    public String getFile() {
        return file;
    }

    public void setFile(String file) {
        // must normalize path to cater for Windows and other OS
        this.file = FileUtil.normalizePath(file);
    }

    public String toString() {
        return file;
    }

}
