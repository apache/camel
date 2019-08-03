/*
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

import org.apache.camel.spi.UriParams;
import org.apache.camel.util.FileUtil;

@UriParams
public class GenericFileConfiguration {

    protected String directory;

    public boolean needToNormalize() {
        return true;
    }

    public void configure(URI uri) {
        String path = uri.getPath();
        // strip tailing slash which URI path always start with
        path = FileUtil.stripFirstLeadingSeparator(path);
        setDirectory(path);
    }

    public String getDirectory() {
        return directory;
    }

    public void setDirectory(String directory) {
        this.directory = needToNormalize()
            // must normalize path to cater for Windows and other OS
            ? FileUtil.normalizePath(directory)
            // for the remote directory we don't need to do that
            : directory;

        // endpoint directory must not be null
        if (this.directory == null) {
            this.directory = "";
        }
    }

    @Override
    public String toString() {
        return directory;
    }

}
