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
package org.apache.camel.component.mongodb.gridfs;

public final class GridFsConstants {

    public static final String GRIDFS_FILE_ATTRIBUTE_DONE = "done";
    public static final String GRIDFS_FILE_ATTRIBUTE_PROCESSING = "processing";
    public static final String GRIDFS_FILE_KEY_CONTENT_TYPE = "contentType";
    public static final String GRIDFS_FILE_KEY_FILENAME = "filename";
    public static final String GRIDFS_FILE_KEY_UPLOAD_DATE = "uploadDate";
    public static final String PERSISTENT_TIMESTAMP_KEY = "timestamp";

    private GridFsConstants() {
    }
}
