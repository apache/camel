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
package org.apache.camel.component.dropbox.util;

import org.apache.camel.spi.Metadata;

public interface DropboxConstants {

    String DROPBOX_FILE_SEPARATOR = "/";
    long POLL_CONSUMER_DELAY = 60 * 60 * 1000L;

    @Metadata(label = "all", description = "The remote path", javaType = "String")
    String HEADER_REMOTE_PATH = "CamelDropboxRemotePath";
    @Metadata(label = "move", description = "The new remote path", javaType = "String")
    String HEADER_NEW_REMOTE_PATH = "CamelDropboxNewRemotePath";
    @Metadata(label = "put", description = "The local path", javaType = "String")
    String HEADER_LOCAL_PATH = "CamelDropboxLocalPath";
    @Metadata(label = "put", description = "The upload mode", javaType = "String")
    String HEADER_UPLOAD_MODE = "CamelDropboxUploadMode";
    @Metadata(label = "search", description = "The query", javaType = "String")
    String HEADER_QUERY = "CamelDropboxQuery";
    @Metadata(label = "put", description = "The name of the file to upload", javaType = "String")
    String HEADER_PUT_FILE_NAME = "CamelDropboxPutFileName";
    @Metadata(label = "get", description = "In case of single file download, path of the remote file downloaded",
              javaType = "String")
    String DOWNLOADED_FILE = DropboxResultHeader.DOWNLOADED_FILE.name();
    @Metadata(label = "get", description = "In case of multiple files download, path of the remote files downloaded",
              javaType = "String")
    String DOWNLOADED_FILES = DropboxResultHeader.DOWNLOADED_FILES.name();
    @Metadata(label = "put", description = "In case of single file upload, path of the remote path uploaded",
              javaType = "String")
    String UPLOADED_FILE = DropboxResultHeader.UPLOADED_FILE.name();
    @Metadata(label = "put", description = "In case of multiple files upload, string with the remote paths uploaded",
              javaType = "String")
    String UPLOADED_FILES = DropboxResultHeader.UPLOADED_FILES.name();
    @Metadata(label = "search", description = "List of file path founded", javaType = "String")
    String FOUND_FILES = DropboxResultHeader.FOUND_FILES.name();
    @Metadata(label = "del", description = "Name of the path deleted on dropbox", javaType = "String")
    String DELETED_PATH = DropboxResultHeader.DELETED_PATH.name();
    @Metadata(label = "move", description = "Name of the path moved on dropbox", javaType = "String")
    String MOVED_PATH = DropboxResultHeader.MOVED_PATH.name();
}
