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
package org.apache.camel.component.atmos.util;

import org.apache.camel.spi.Metadata;

public final class AtmosConstants {

    public static final String ATMOS_FILE_SEPARATOR = "/";
    public static final long POLL_CONSUMER_DELAY = 60 * 60 * 1000L;

    @Metadata(description = "The name of the remote path downloaded in case of a single file.", javaType = "java.lang.String")
    public static final String DOWNLOADED_FILE = "DOWNLOADED_FILE";
    @Metadata(description = "The name of the remote paths downloaded in case of multiple files (one per line).",
              javaType = "java.lang.String")
    public static final String DOWNLOADED_FILES = "DOWNLOADED_FILES";
    @Metadata(label = "producer", description = "The name of the remote path uploaded in case of a single file.",
              javaType = "java.lang.String")
    public static final String UPLOADED_FILE = "UPLOADED_FILE";
    @Metadata(label = "producer",
              description = "The name of the remote paths uploaded in case of multiple files (one per line).",
              javaType = "java.lang.String")
    public static final String UPLOADED_FILES = "UPLOADED_FILES";
    @Metadata(label = "producer", description = "The remote path deleted on Atmos.", javaType = "java.lang.String")
    public static final String DELETED_PATH = "DELETED_PATH";
    @Metadata(label = "producer", description = "The moved path.", javaType = "java.lang.String")
    public static final String MOVED_PATH = "MOVED_PATH";

    private AtmosConstants() {
    }

}
