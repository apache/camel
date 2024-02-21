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

import org.apache.camel.Exchange;
import org.apache.camel.spi.Metadata;

public final class GridFsConstants {

    @Metadata(label = "consumer", description = "The content type of the file.", javaType = "String")
    public static final String FILE_CONTENT_TYPE = Exchange.FILE_CONTENT_TYPE;
    @Metadata(label = "consumer", description = "The size of the file.", javaType = "long")
    public static final String FILE_LENGTH = Exchange.FILE_LENGTH;
    @Metadata(label = "consumer", description = "The size of the file.", javaType = "Date")
    public static final String FILE_LAST_MODIFIED = Exchange.FILE_LAST_MODIFIED;
    @Metadata(label = "producer", description = "The name of the file.", javaType = "String")
    public static final String FILE_NAME = Exchange.FILE_NAME;
    @Metadata(label = "producer", description = "The content type of the file.", javaType = "String")
    public static final String CONTENT_TYPE = Exchange.CONTENT_TYPE;
    @Metadata(label = "producer", description = "The file name produced.", javaType = "String")
    public static final String FILE_NAME_PRODUCED = Exchange.FILE_NAME_PRODUCED;

    public static final String GRIDFS_FILE_ATTRIBUTE_DONE = "done";
    public static final String GRIDFS_FILE_ATTRIBUTE_PROCESSING = "processing";
    public static final String GRIDFS_FILE_KEY_CONTENT_TYPE = "contentType";
    public static final String GRIDFS_FILE_KEY_FILENAME = "filename";
    public static final String GRIDFS_FILE_KEY_UPLOAD_DATE = "uploadDate";
    public static final String PERSISTENT_TIMESTAMP_KEY = "timestamp";
    @Metadata(description = "Any additional metadata stored along with the file in JSON format.", javaType = "String")
    public static final String GRIDFS_METADATA = "gridfs.metadata";
    @Metadata(label = "producer", description = "The operation to perform.", javaType = "String")
    public static final String GRIDFS_OPERATION = "gridfs.operation";
    @Metadata(label = "producer", description = "The number of bytes per chunk for the uploaded file.", javaType = "Integer")
    public static final String GRIDFS_CHUNKSIZE = "gridfs.chunksize";
    @Metadata(label = "producer", description = "The ObjectId of the file produced", javaType = "org.bson.types.ObjectId")
    public static final String GRIDFS_FILE_ID_PRODUCED = "gridfs.fileid";
    @Metadata(label = "producer", description = "The ObjectId of the file.", javaType = "org.bson.types.ObjectId")
    public static final String GRIDFS_OBJECT_ID = "gridfs.objectid";

    private GridFsConstants() {
    }
}
