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
package org.apache.camel.component.whatsapp.model;

import java.io.File;
import java.io.InputStream;
import java.util.Objects;

import org.apache.camel.RuntimeCamelException;

public class UploadMedia {

    private File file;
    private String contentType;
    private String name;
    private InputStream fileStream;

    public UploadMedia(File file, String contentType) {
        this.file = Objects.requireNonNull(file);
        this.contentType = Objects.requireNonNull(contentType);

        if (!file.exists()) {
            throw new RuntimeCamelException("The file provided does not exist");
        }
    }

    public UploadMedia(String name, InputStream fileStream, String contentType) {
        this.name = Objects.requireNonNull(name);
        this.fileStream = Objects.requireNonNull(fileStream);
        this.contentType = Objects.requireNonNull(contentType);
    }

    public File getFile() {
        return file;
    }

    public String getContentType() {
        return contentType;
    }

    public String getName() {
        return name;
    }

    public InputStream getFileStream() {
        return fileStream;
    }
}
