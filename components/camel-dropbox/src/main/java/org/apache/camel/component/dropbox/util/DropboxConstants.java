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

public interface DropboxConstants {

    String DROPBOX_FILE_SEPARATOR = "/";
    long POLL_CONSUMER_DELAY = 60 * 60 * 1000L;

    String HEADER_REMOTE_PATH = "CamelDropboxRemotePath";
    String HEADER_NEW_REMOTE_PATH = "CamelDropboxNewRemotePath";
    String HEADER_LOCAL_PATH = "CamelDropboxLocalPath";
    String HEADER_UPLOAD_MODE = "CamelDropboxUploadMode";
    String HEADER_QUERY = "CamelDropboxQuery";
    String HEADER_PUT_FILE_NAME = "CamelDropboxPutFileName";
}
