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
package org.apache.camel.attachment;

import java.io.File;

import jakarta.activation.FileDataSource;
import jakarta.activation.FileTypeMap;

import org.apache.camel.util.MimeTypeHelper;

/**
 * A {@link FileDataSource} that uses the file name/extension to determine the content-type.
 */
public class CamelFileDataSource extends FileDataSource {
    private final String fileName;
    private FileTypeMap typeMap;

    public CamelFileDataSource(File file) {
        this(file, file.getName());
    }

    public CamelFileDataSource(File file, String fileName) {
        super(file);
        this.fileName = fileName;
    }

    @Override
    public String getContentType() {
        String answer = MimeTypeHelper.probeMimeType(fileName);
        if (answer == null) {
            if (typeMap == null) {
                answer = FileTypeMap.getDefaultFileTypeMap().getContentType(fileName);
            } else {
                answer = typeMap.getContentType(fileName);
            }
        }
        return answer;
    }

    @Override
    public void setFileTypeMap(FileTypeMap map) {
        typeMap = map;
    }

    @Override
    public String getName() {
        if (fileName != null) {
            return fileName;
        } else {
            return super.getName();
        }
    }

}
