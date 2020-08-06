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
package org.apache.camel.component.minio;

/**
 * Constants used in Camel Minio module
 */
public interface MinioConstants {

    int BYTE_ARRAY_LENGTH = 1024;
    String BUCKET_NAME = "CamelMinioBucketName";
    String DESTINATION_BUCKET_NAME = "CamelMinioDestinationBucketName";
    String CACHE_CONTROL = "CamelMinioContentControl";
    String CONTENT_DISPOSITION = "CamelMinioContentDisposition";
    String CONTENT_ENCODING = "CamelMinioContentEncoding";
    String CONTENT_LENGTH = "CamelMinioContentLength";
    String CONTENT_MD5 = "CamelMinioContentMD5";
    String CONTENT_TYPE = "CamelMinioContentType";
    String E_TAG = "CamelMinioETag";
    String OBJECT_NAME = "CamelMinioObjectName";
    String DESTINATION_OBJECT_NAME = "CamelMinioDestinationObjectName";
    String LAST_MODIFIED = "CamelMinioLastModified";
    String STORAGE_CLASS = "CamelMinioStorageClass";
    String VERSION_ID = "CamelMinioVersionId";
    String CANNED_ACL = "CamelMinioCannedAcl";
    String MINIO_OPERATION = "CamelMinioOperation";
    String SERVER_SIDE_ENCRYPTION = "CamelMinioServerSideEncryption";
    String EXPIRATION_TIME = "CamelMinioExpirationTime";
    String REPLICATION_STATUS = "CamelMinioReplicationStatus";
    String OFFSET = "CamelMinioOffset";
    String LENGTH = "CamelMinioLength";
}
