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

import org.apache.camel.spi.Metadata;

/**
 * Constants used in Camel Minio module
 */
@Deprecated(since = "4.23")
public interface MinioConstants {

    int BYTE_ARRAY_LENGTH = 1024;
    @Metadata(description = "The name of the bucket of the consumed object. The consumer sets it. The producer does not "
                            + "read it: the CamelMinioOverrideBucketName header sets the bucket of the producer.",
              javaType = "String")
    String BUCKET_NAME = "CamelMinioBucketName";
    @Metadata(label = "producer",
              description = "The bucket that the producer copies the object to, for copyObject, which requires it.",
              javaType = "String")
    String DESTINATION_BUCKET_NAME = "CamelMinioDestinationBucketName";
    @Metadata(description = "The Cache-Control of the object. The consumer sets it from the consumed object. The "
                            + "producer stores it as user metadata (x-amz-meta-Cache-Control) of the object it uploads.",
              javaType = "String")
    String CACHE_CONTROL = "CamelMinioContentControl";
    @Metadata(description = "The Content-Disposition of the object. The consumer sets it from the consumed object. The "
                            + "producer stores it as user metadata (x-amz-meta-Content-Disposition) of the object it "
                            + "uploads.",
              javaType = "String")
    String CONTENT_DISPOSITION = "CamelMinioContentDisposition";
    @Metadata(description = "The Content-Encoding of the object. The consumer sets it from the consumed object. The "
                            + "producer stores it as user metadata (x-amz-meta-Content-Encoding) of the object it "
                            + "uploads.",
              javaType = "String")
    String CONTENT_ENCODING = "CamelMinioContentEncoding";
    @Metadata(description = "The size of the object in bytes. The consumer sets it for the consumed object. The "
                            + "producer uses it as the length of a body whose length it cannot tell without reading it.",
              javaType = "Long")
    String CONTENT_LENGTH = "CamelMinioContentLength";
    @Metadata(description = "The Content-MD5 of the object that the producer uploads, the Base64 encoded MD5 digest of "
                            + "its content. The producer stores it as user metadata (x-amz-meta-Content-Md5) of the "
                            + "object.",
              javaType = "String")
    String CONTENT_MD5 = "CamelMinioContentMD5";
    @Metadata(description = "The Content-Type of the object. The consumer sets it from the consumed object. The "
                            + "producer stores it as user metadata (x-amz-meta-Content-Type) of the object it uploads.",
              javaType = "String")
    String CONTENT_TYPE = "CamelMinioContentType";
    @Metadata(description = "The entity tag (ETag) of the object. The consumer sets it for the consumed object, and the "
                            + "producer for the object it uploaded.",
              javaType = "String")
    String E_TAG = "CamelMinioETag";
    @Metadata(description = "The name of the object. The consumer sets it for the consumed object. The producer reads "
                            + "it as the object to upload, get, delete, copy or create a link for, and it replaces the "
                            + "keyName option.",
              javaType = "String")
    String OBJECT_NAME = "CamelMinioObjectName";
    @Metadata(label = "producer",
              description = "The name of the object that the producer copies to, for copyObject, which requires it.",
              javaType = "String")
    String DESTINATION_OBJECT_NAME = "CamelMinioDestinationObjectName";
    @Metadata(description = "The value of the Last-Modified HTTP header of the consumed object. The consumer sets it.",
              javaType = "java.util.Date")
    String LAST_MODIFIED = "CamelMinioLastModified";
    @Metadata(label = "producer",
              description = "The storage class of the object. The consumer sets it from the consumed object when the "
                            + "server returns one. The producer reads it as the storage class of the object it uploads, "
                            + "and it replaces the storageClass option.",
              javaType = "String")
    String STORAGE_CLASS = "CamelMinioStorageClass";
    @Metadata(description = "The version ID of the object, when versioning is enabled on the bucket. The consumer sets "
                            + "it for the consumed object, and the producer for the object it uploaded or copied with "
                            + "copyObject.",
              javaType = "String")
    String VERSION_ID = "CamelMinioVersionId";
    @Metadata(label = "producer",
              description = "The canned ACL of the object that the producer uploads, sent as the x-amz-acl header, for "
                            + "example private or public-read.",
              javaType = "String")
    String CANNED_ACL = "CamelMinioCannedAcl";
    @Metadata(label = "producer", description = "The operation that the producer performs. It replaces the operation option.",
              javaType = "org.apache.camel.component.minio.MinioOperations",
              enums = "copyObject,listObjects,deleteObject,deleteObjects,deleteBucket,listBuckets,getObject,getObjectRange,createDownloadLink,createUploadLink")
    String MINIO_OPERATION = "CamelMinioOperation";
    @Metadata(description = "The server-side encryption algorithm of the consumed object, from the "
                            + "x-amz-server-side-encryption header, for example AES256. The consumer sets it. The "
                            + "producer does not read it: the serverSideEncryption option sets the encryption of uploads.",
              javaType = "String")
    String SERVER_SIDE_ENCRYPTION = "CamelMinioServerSideEncryption";
    @Metadata(description = "The value of the x-amz-expiration header of the consumed object: when a lifecycle rule of "
                            + "the bucket expires the object. The consumer sets it.",
              javaType = "String")
    String EXPIRATION_TIME = "CamelMinioExpirationTime";
    @Metadata(description = "The replication status of the consumed object, from the x-amz-replication-status header. "
                            + "The consumer sets it.",
              javaType = "String")
    String REPLICATION_STATUS = "CamelMinioReplicationStatus";
    @Metadata(label = "producer",
              description = "The position of the first byte that the producer gets, for getObjectRange, which requires "
                            + "it.",
              javaType = "String")
    String OFFSET = "CamelMinioOffset";
    @Metadata(label = "producer",
              description = "The number of bytes, from CamelMinioOffset, that the producer gets, for getObjectRange, "
                            + "which requires it.",
              javaType = "String")
    String LENGTH = "CamelMinioLength";
    @Metadata(label = "producer",
              description = "How long, in seconds, the URL of createDownloadLink and createUploadLink is valid: from 1 "
                            + "to 604800 (7 days). The default is 3600 (one hour).",
              javaType = "int")
    String PRESIGNED_URL_EXPIRATION_TIME = "CamelMinioPresignedURLExpirationTime";
    @Metadata(description = "The name of the bucket for the producer. It replaces the bucket of the endpoint.",
              javaType = "String")
    String OVERRIDE_BUCKET_NAME = "CamelMinioOverrideBucketName";
}
