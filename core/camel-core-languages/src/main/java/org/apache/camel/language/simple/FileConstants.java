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
package org.apache.camel.language.simple;

import org.apache.camel.spi.Metadata;

@Metadata(label = "function", annotations = { "prefix=${", "suffix=}" })
public final class FileConstants {

    @Metadata(description = "The file name (relative from starting directory)", javaType = "String", label = "file")
    public static final String FILE_NAME = "file:name";
    @Metadata(description = "The file name (relative from starting directory) without extension", javaType = "String",
              label = "file")
    public static final String FILE_NO_EXT = "file:name.noext";
    @Metadata(description = "The file name (relative from starting directory) without extension. If the file name has multiple dots, then this expression strips and only returns the last part.",
              javaType = "String", label = "file")
    public static final String FILE_NO_EXT_SINGLE = "file:name.noext.single";
    @Metadata(description = "The file extension", javaType = "String", label = "file")
    public static final String FILE_EXT = "file:name.ext";
    @Metadata(description = "The file extension. If the file extension has multiple dots, then this expression strips and only returns the last part.",
              javaType = "String", label = "file")
    public static final String FILE_EXT_SINGLE = "file:name.ext.single";
    @Metadata(description = "†he file name (without any leading paths)", javaType = "String", label = "file")
    public static final String FILE_ONLY_NAME = "file:onlyname";
    @Metadata(description = "†he file name (without any leading paths) without extension", javaType = "String",
              label = "file")
    public static final String FILE_ONLY_NAME_NO_EXT = "file:onlyname.noext";
    @Metadata(description = "†he file name (without any leading paths) without extension. If the file name has multiple dots, then this expression strips and only returns the last part.",
              javaType = "String", label = "file")
    public static final String FILE_ONLY_NAME_NO_EXT_SINGLE = "file:onlyname.noext.single";
    @Metadata(description = "The file parent directory (null if no parent directory)", javaType = "String", label = "file")
    public static final String FILE_PARENT = "file:parent";
    @Metadata(description = "The file path", javaType = "String", label = "file")
    public static final String FILE_PATH = "file:path";
    @Metadata(description = "Whether the file is regarded as absolute or relative", javaType = "boolean", label = "file")
    public static final String FILE_ABSOLUTE = "file:absolute";
    @Metadata(description = "The absolute file path", javaType = "String", label = "file")
    public static final String FILE_ABSOLUTE_PATH = "file:absolute.path";
    @Metadata(description = "The size of the file", javaType = "long", label = "file")
    public static final String FILE_LENGTH = "file:length";
    @Metadata(description = "The size of the file", javaType = "long", label = "file")
    public static final String FILE_SIZE = "file:size";
    @Metadata(description = "The file modification date", javaType = "long", label = "file")
    public static final String FILE_MODIFIED = "file:modified";
}
