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

    @Metadata(description = "The type of the event: CREATE, MODIFY or DELETE.", javaType = "String")
    public static final String EVENT_TYPE_HEADER = "CamelFileEventType";
    @Metadata(description = "Only the file name (the name with no leading paths).", javaType = "String")
    public static final String FILE_NAME_ONLY = Exchange.FILE_NAME_ONLY;
    @Metadata(description = "Always true, as the CamelFileAbsolutePath and CamelFilePath headers hold an absolute path.",
              javaType = "Boolean")
    public static final String FILE_ABSOLUTE = "CamelFileAbsolute";
    @Metadata(description = "The absolute path of the file or directory of the event, normalized and with / as separator.",
              javaType = "String")
    public static final String FILE_ABSOLUTE_PATH = "CamelFileAbsolutePath";
    @Metadata(description = "The absolute path of the file or directory of the event, the same value as the"
                            + " CamelFileAbsolutePath header.",
              javaType = "String")
    public static final String FILE_PATH = Exchange.FILE_PATH;
    @Metadata(description = "The path of the file or directory of the event relative to the directory of the path option, with"
                            + " / as separator.",
              javaType = "String", important = true)
    public static final String FILE_NAME = Exchange.FILE_NAME;
    @Metadata(description = "The path relative to the directory of the path option, the same value as the CamelFileName"
                            + " header.",
              javaType = "String")
    public static final String FILE_RELATIVE_PATH = "CamelFileRelativePath";
    @Metadata(description = "The path relative to the directory of the path option, the same value as the CamelFileName"
                            + " header.",
              javaType = "String")
    public static final String FILE_NAME_CONSUMED = Exchange.FILE_NAME_CONSUMED;
    @Metadata(description = "The absolute path of the directory that holds the file or directory of the event, with / as"
                            + " separator.",
              javaType = "String")
    public static final String FILE_PARENT = Exchange.FILE_PARENT;
    @Metadata(description = "The last modified time of the file or directory, in milliseconds since the epoch. Not set when"
                            + " the file no longer exists, such as for a DELETE event.",
              javaType = "long")
    public static final String FILE_LAST_MODIFIED = Exchange.FILE_LAST_MODIFIED;
    @Metadata(description = "The time the consumer received the event, in milliseconds since the epoch.",
              javaType = "long")
    public static final String MESSAGE_TIMESTAMP = Exchange.MESSAGE_TIMESTAMP;

    private FileWatchConstants() {
        // Utility class
    }
}
