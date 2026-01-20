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
package org.apache.camel.component.ibm.watsonx.ai.support;

import java.io.File;
import java.io.InputStream;

/**
 * Represents file input that can be either a File or InputStream with fileName.
 */
public record FileInput(File file, InputStream inputStream, String fileName) {

    public static FileInput of(File file) {
        return new FileInput(file, null, file.getName());
    }

    public static FileInput of(InputStream is, String fileName) {
        return new FileInput(null, is, fileName);
    }

    public boolean isFile() {
        return file != null;
    }

    public boolean isInputStream() {
        return inputStream != null;
    }
}
