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
package org.apache.camel.component.file.azure;

import org.apache.camel.Exchange;
import org.apache.camel.component.file.remote.RemoteFileComponent;
import org.apache.camel.spi.Metadata;

public final class FilesConstants {

    @Metadata(label = "consumer",
              description = "The size of the file in bytes, which the consumer sets. The producer uses it as the size of the"
                            + " file it creates when the body is a stream of unknown length.",
              javaType = "long")
    public static final String FILE_LENGTH = Exchange.FILE_LENGTH;
    @Metadata(label = "consumer",
              description = "The time that the file was last modified, in milliseconds since the epoch, as the file service"
                            + " lists it.",
              javaType = "long")
    public static final String FILE_LAST_MODIFIED = Exchange.FILE_LAST_MODIFIED;
    @Metadata(label = "consumer", description = "Only the file name (the name with no leading paths).", javaType = "String")
    public static final String FILE_NAME_ONLY = Exchange.FILE_NAME_ONLY;
    @Metadata(description = "The name of the file, relative to the directory of the endpoint. The consumer sets it for the"
                            + " consumed file. The producer writes the file under this name, a String or an Expression,"
                            + " unless the fileName option is set; with neither, it uses the message ID.",
              javaType = "String")
    public static final String FILE_NAME = Exchange.FILE_NAME;
    @Metadata(label = "consumer",
              description = "The name of the consumed file, relative to the directory of the endpoint. Unlike CamelFileName,"
                            + " it keeps this value when the route changes CamelFileName.",
              javaType = "String")
    public static final String FILE_NAME_CONSUMED = Exchange.FILE_NAME_CONSUMED;
    @Metadata(label = "consumer",
              description = "Whether the path of the consumed file in CamelFileAbsolutePath is absolute, that is, starts with"
                            + " / at the share root.",
              javaType = "Boolean")
    public static final String FILE_ABSOLUTE = "CamelFileAbsolute";
    @Metadata(label = "consumer",
              description = "The path of the consumed file from the share root, without the share name, for example"
                            + " /inbox/order.xml.",
              javaType = "String")
    public static final String FILE_ABSOLUTE_PATH = "CamelFileAbsolutePath";
    @Metadata(label = "consumer",
              description = "The path of the consumed file from the share root, the same value as CamelFileAbsolutePath.",
              javaType = "String")
    public static final String FILE_PATH = Exchange.FILE_PATH;
    @Metadata(label = "consumer",
              description = "The path of the consumed file relative to the directory of the endpoint, for example"
                            + " 2026/order.xml for a file in a subdirectory with recursive=true.",
              javaType = "String")
    public static final String FILE_RELATIVE_PATH = "CamelFileRelativePath";
    @Metadata(description = "The path of the directory that holds the consumed file, from the share root, for example"
                            + " /inbox.",
              javaType = "String")
    public static final String FILE_PARENT = Exchange.FILE_PARENT;
    @Metadata(label = "producer",
              description = "The path of the file that the producer wrote, from the share root, for example"
                            + " /outbox/order.xml.",
              javaType = "String")
    public static final String FILE_NAME_PRODUCED = Exchange.FILE_NAME_PRODUCED;
    @Metadata(label = "producer",
              description = "A file name, a String or an Expression, that the producer uses instead of CamelFileName for one"
                            + " write only: the producer removes this header after the write. The fileName option, if set,"
                            + " still takes precedence.",
              javaType = "Object")
    public static final String OVERRULE_FILE_NAME = Exchange.OVERRULE_FILE_NAME;

    @Metadata(description = "The input stream of the content of the consumed file, with streamDownload=true. The consumer"
                            + " closes it when the exchange is done.",
              javaType = "java.io.InputStream")
    public static final String REMOTE_FILE_INPUT_STREAM = RemoteFileComponent.REMOTE_FILE_INPUT_STREAM;
    @Metadata(description = "The path of the local file in localWorkDirectory that holds the downloaded content of the"
                            + " consumed file. The consumer deletes this file when the exchange is done.",
              javaType = "String")
    public static final String FILE_LOCAL_WORK_PATH = Exchange.FILE_LOCAL_WORK_PATH;

    @Metadata(description = "The host name of the file service of the storage account, for example"
                            + " myaccount.file.core.windows.net. The consumer sets it.",
              javaType = "String")
    public static final String FILE_HOST = "CamelFileHost";
    @Metadata(label = "consumer", description = "The name of the share the file was consumed from.", javaType = "String")
    public static final String SHARE_NAME = "CamelAzureFilesShareName";

    private FilesConstants() {
    }
}
