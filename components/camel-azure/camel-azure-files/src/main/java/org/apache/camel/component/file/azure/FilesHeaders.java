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

public final class FilesHeaders {

    @Metadata(label = "both", description = "A `long` value containing the file size. For producer,"
                                            + " known length helps if the body converts to InputStream"
                                            + " more efficiently than to bytes array.",
              javaType = "long")
    public static final String FILE_LENGTH = Exchange.FILE_LENGTH;
    @Metadata(label = "consumer", description = "A `Long` value containing the last modified timestamp of the file.",
              javaType = "long")
    public static final String FILE_LAST_MODIFIED = Exchange.FILE_LAST_MODIFIED;
    @Metadata(description = "Specifies the output file name (relative to the endpoint directory) to\n" +
                            "be used for the output message when sending to the endpoint. If this is\n" +
                            "not present and no expression either, then a generated message ID is\n" +
                            "used as the filename instead.",
              javaType = "String")
    public static final String FILE_NAME = Exchange.FILE_NAME;
    @Metadata(description = "Only the file name (the name with no leading paths).", javaType = "String")
    public static final String FILE_NAME_ONLY = Exchange.FILE_NAME_ONLY;
    @Metadata(description = "The parent path.", javaType = "String")
    public static final String FILE_PARENT = Exchange.FILE_PARENT;
    @Metadata(description = "The remote file input stream.", javaType = "java.io.InputStream")
    public static final String REMOTE_FILE_INPUT_STREAM = RemoteFileComponent.REMOTE_FILE_INPUT_STREAM;
    @Metadata(description = "Path to the local work file, if local work directory is used.", javaType = "String")
    public static final String FILE_LOCAL_WORK_PATH = Exchange.FILE_LOCAL_WORK_PATH;

    @Metadata(description = "The remote hostname.", javaType = "String")
    public static final String FILE_HOST = "CamelFileHost";

    private FilesHeaders() {
    }
}
