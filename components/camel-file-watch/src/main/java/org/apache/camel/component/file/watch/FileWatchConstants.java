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
package org.apache.camel.component.file.watch;

import org.apache.camel.Exchange;
import org.apache.camel.spi.Metadata;

public final class FileWatchConstants {

    @Metadata(description = "Type of event. Possible values: CREATE, DELETE, MODIFY.", javaType = "String")
    public static final String EVENT_TYPE_HEADER = "CamelFileEventType";
    @Metadata(description = "Only the file name (the name with no leading paths).", javaType = "String")
    public static final String FILE_NAME_ONLY = Exchange.FILE_NAME_ONLY;
    @Metadata(description = "A `boolean` option specifying whether the consumed file denotes an\n" +
                            "absolute path or not. Should normally be `false` for relative paths.\n" +
                            "Absolute paths should normally not be used but we added to the move\n" +
                            "option to allow moving files to absolute paths. But can be used\n" +
                            "elsewhere as well.",
              javaType = "Boolean")
    public static final String FILE_ABSOLUTE = "CamelFileAbsolute";
    @Metadata(description = "The absolute path to the file. For relative files this path holds the\n" +
                            "relative path instead.",
              javaType = "String")
    public static final String FILE_ABSOLUTE_PATH = "CamelFileAbsolutePath";
    @Metadata(description = "The file path. For relative files this is the starting directory + the\n" +
                            "relative filename. For absolute files this is the absolute path.",
              javaType = "String")
    public static final String FILE_PATH = Exchange.FILE_PATH;
    @Metadata(description = "Name of the consumed file as a relative file path with offset from the\n" +
                            "starting directory configured on the endpoint.",
              javaType = "String")
    public static final String FILE_NAME = Exchange.FILE_NAME;
    @Metadata(description = "The relative path.", javaType = "String")
    public static final String FILE_RELATIVE_PATH = "CamelFileRelativePath";
    @Metadata(description = "The name of the file that has been consumed", javaType = "String")
    public static final String FILE_NAME_CONSUMED = Exchange.FILE_NAME_CONSUMED;
    @Metadata(description = "The parent path.", javaType = "String")
    public static final String FILE_PARENT = Exchange.FILE_PARENT;
    @Metadata(description = "A `Long` value containing the last modified timestamp of the file.",
              javaType = "long")
    public static final String FILE_LAST_MODIFIED = Exchange.FILE_LAST_MODIFIED;

    private FileWatchConstants() {
        // Utility class
    }
}
