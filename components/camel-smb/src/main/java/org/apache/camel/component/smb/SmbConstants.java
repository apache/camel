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
package org.apache.camel.component.smb;

import org.apache.camel.Exchange;
import org.apache.camel.spi.Metadata;

public class SmbConstants {

    @Metadata(label = "consumer", description = "A `long` value containing the file size.", javaType = "long")
    public static final String FILE_LENGTH = Exchange.FILE_LENGTH;
    @Metadata(label = "consumer", description = "A `Long` value containing the last modified timestamp of the file.",
              javaType = "long")
    public static final String FILE_LAST_MODIFIED = Exchange.FILE_LAST_MODIFIED;
    @Metadata(label = "consumer", description = "Only the file name (the name with no leading paths).", javaType = "String")
    public static final String FILE_NAME_ONLY = Exchange.FILE_NAME_ONLY;
    @Metadata(description = "(producer) Specifies the name of the file to write (relative to the endpoint"
                            + " directory). This name can be a `String`; a `String` with a"
                            + " file or simple Language expression; or an Expression object. If it's"
                            + " `null` then Camel will auto-generate a filename based on the message"
                            + " unique ID. (consumer) Name of the consumed file as a relative file path with offset from the"
                            + " starting directory configured on the endpoint.",
              javaType = "String")
    public static final String FILE_NAME = Exchange.FILE_NAME;
    @Metadata(label = "consumer", description = "The name of the file that has been consumed", javaType = "String")
    public static final String FILE_NAME_CONSUMED = Exchange.FILE_NAME_CONSUMED;
    @Metadata(label = "consumer", description = "A `boolean` option specifying whether the consumed file denotes an"
                                                + " absolute path or not. Should normally be `false` for relative paths."
                                                + " Absolute paths should normally not be used but we added to the move"
                                                + " option to allow moving files to absolute paths. But can be used"
                                                + " elsewhere as well.",
              javaType = "Boolean")
    public static final String FILE_ABSOLUTE = "CamelFileAbsolute";
    @Metadata(label = "consumer", description = "The absolute path to the file. For relative files this path holds the"
                                                + " relative path instead.",
              javaType = "String")
    public static final String FILE_ABSOLUTE_PATH = "CamelFileAbsolutePath";
    @Metadata(label = "consumer", description = "The file path. For relative files this is the starting directory."
                                                + " For absolute files this is the absolute path.",
              javaType = "String")
    public static final String FILE_PATH = Exchange.FILE_PATH;
    @Metadata(label = "consumer", description = "The relative path.", javaType = "String")
    public static final String FILE_RELATIVE_PATH = "CamelFileRelativePath";
    @Metadata(description = "The parent path.", javaType = "String")
    public static final String FILE_PARENT = Exchange.FILE_PARENT;
    @Metadata(label = "producer", description = "The actual absolute filepath (path + name) for the output file that was"
                                                + " written. This header is set by Camel and its purpose is providing"
                                                + " end-users with the name of the file that was written.",
              javaType = "String")
    public static final String FILE_NAME_PRODUCED = Exchange.FILE_NAME_PRODUCED;
    @Metadata(label = "producer", description = "Is used for overruling `CamelFileName` header and use the"
                                                + " value instead (but only once, as the producer will remove this header"
                                                + " after writing the file). The value can be only be a String. Notice that"
                                                + " if the option `fileName` has been configured, then this is still being"
                                                + " evaluated.",
              javaType = "Object")
    public static final String OVERRULE_FILE_NAME = Exchange.OVERRULE_FILE_NAME;

    @Metadata(label = "consumer", description = "The remote hostname.", javaType = "String")
    public static final String FILE_HOST = "CamelFileHost";
    @Metadata(label = "consumer", description = "The remote file input stream.", javaType = "java.io.InputStream")
    public static final String SMB_FILE_INPUT_STREAM = SmbComponent.SMB_FILE_INPUT_STREAM;
    @Metadata(description = "Path to the local work file, if local work directory is used.", javaType = "String")
    public static final String FILE_LOCAL_WORK_PATH = Exchange.FILE_LOCAL_WORK_PATH;
    // for backwards compatibility with existing 'file-exists' that uses a header
    @Metadata(label = "producer", description = "The expected behavior if the file already exists.",
              javaType = "org.apache.camel.component.file.GenericFileExist")
    @Deprecated
    public static final String SMB_FILE_EXISTS = "CamelSmbFileExists";
}
