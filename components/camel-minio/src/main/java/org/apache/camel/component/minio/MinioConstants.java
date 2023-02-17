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
public interface MinioConstants {

    int BYTE_ARRAY_LENGTH = 1024;
    @Metadata(description = "*Producer:* The bucket Name which this object will be stored or which will be used for the current operation. "
                            +
                            "*Consumer:* The name of the bucket in which this object is contained.",
              javaType = "String")
    String BUCKET_NAME = "CamelMinioBucketName";
    @Metadata(label = "producer", description = "The bucket Destination Name which will be used for the current operation.",
              javaType = "String")
    String DESTINATION_BUCKET_NAME = "CamelMinioDestinationBucketName";
    @Metadata(description = "*Producer:* The content control of this object. " +
                            "*Consumer:* The *optional* Cache-Control HTTP header which allows the user to\n" +
                            "specify caching behavior along the HTTP request/reply chain.",
              javaType = "String")
    String CACHE_CONTROL = "CamelMinioContentControl";
    @Metadata(description = "*Producer:* The content disposition of this object. " +
                            "*Consumer:* The *optional* Content-Disposition HTTP header, which specifies\n" +
                            "presentational information such as the recommended filename for the\n" +
                            "object to be saved as.",
              javaType = "String")
    String CONTENT_DISPOSITION = "CamelMinioContentDisposition";
    @Metadata(description = "*Producer:* The content encoding of this object. " +
                            "*Consumer:* The *optional* Content-Encoding HTTP header specifying what content\n" +
                            "encodings have been applied to the object and what decoding mechanisms\n" +
                            "must be applied in order to obtain the media-type referenced by the\n" +
                            "Content-Type field.",
              javaType = "String")
    String CONTENT_ENCODING = "CamelMinioContentEncoding";
    @Metadata(description = "*Producer:* The content length of this object. " +
                            "*Consumer:* The Content-Length HTTP header indicating the size of the associated\n" +
                            "object in bytes.",
              javaType = "Long")
    String CONTENT_LENGTH = "CamelMinioContentLength";
    @Metadata(description = "*Producer:* The md5 checksum of this object. " +
                            "*Consumer:* The base64 encoded 128-bit MD5 digest of the associated object (content\n" +
                            "- not including headers) according to RFC 1864. This data is used as a\n" +
                            "message integrity check to verify that the data received by Minio is\n" +
                            "the same data that the caller sent.",
              javaType = "String")
    String CONTENT_MD5 = "CamelMinioContentMD5";
    @Metadata(description = "*Producer:* The content type of this object. " +
                            "*Consumer:* The Content-Type HTTP header, which indicates the type of content stored\n" +
                            "in the associated object. The value of this header is a standard MIME\n" +
                            "type.",
              javaType = "String")
    String CONTENT_TYPE = "CamelMinioContentType";
    @Metadata(description = "*Producer:* The ETag value for the newly uploaded object. " +
                            "*Consumer:* The hex encoded 128-bit MD5 digest of the associated object according to\n" +
                            "RFC 1864. This data is used as an integrity check to verify that the\n" +
                            "data received by the caller is the same data that was sent by Minio",
              javaType = "String")
    String E_TAG = "CamelMinioETag";
    @Metadata(description = "*Producer:* The key under which this object will be stored or which will be used for the current operation. "
                            +
                            "*Consumer:* The key under which this object is stored.",
              javaType = "String")
    String OBJECT_NAME = "CamelMinioObjectName";
    @Metadata(label = "producer", description = "The Destination key which will be used for the current operation.",
              javaType = "String")
    String DESTINATION_OBJECT_NAME = "CamelMinioDestinationObjectName";
    @Metadata(description = "*Producer:* The last modified timestamp of this object. " +
                            "*Consumer:* The value of the Last-Modified header, indicating the date and time at\n" +
                            "which Minio last recorded a modification to the associated object.",
              javaType = "java.util.Date")
    String LAST_MODIFIED = "CamelMinioLastModified";
    @Metadata(label = "producer", description = "The storage class of this object.", javaType = "String")
    String STORAGE_CLASS = "CamelMinioStorageClass";
    @Metadata(description = "*Producer:* The version Id of the object to be stored or returned from the current operation. " +
                            "*Consumer:* The version ID of the associated Minio object if available. Version\n" +
                            "IDs are only assigned to objects when an object is uploaded to an Minio bucket that has object versioning enabled.",
              javaType = "String")
    String VERSION_ID = "CamelMinioVersionId";
    @Metadata(label = "producer", description = "The canned acl that will be applied to the object. see\n" +
                                                "`com.amazonaws.services.s3.model.CannedAccessControlList` for allowed\n" +
                                                "values.",
              javaType = "String")
    String CANNED_ACL = "CamelMinioCannedAcl";
    @Metadata(label = "producer", description = "The operation to perform.",
              javaType = "org.apache.camel.component.minio.MinioOperations")
    String MINIO_OPERATION = "CamelMinioOperation";
    @Metadata(description = "*Producer:* Sets the server-side encryption algorithm when encrypting\n" +
                            "the object using Minio-managed keys. For example use AES256. " +
                            "*Consumer:* The server-side encryption algorithm when encrypting the\n" +
                            "object using Minio-managed keys.",
              javaType = "String")
    String SERVER_SIDE_ENCRYPTION = "CamelMinioServerSideEncryption";
    @Metadata(description = "The expiration time", javaType = "String")
    String EXPIRATION_TIME = "CamelMinioExpirationTime";
    @Metadata(description = "The replication status", javaType = "String")
    String REPLICATION_STATUS = "CamelMinioReplicationStatus";
    @Metadata(label = "producer", description = "The offset", javaType = "String")
    String OFFSET = "CamelMinioOffset";
    @Metadata(label = "producer", description = "The length", javaType = "String")
    String LENGTH = "CamelMinioLength";
    @Metadata(label = "producer", description = "Expiration of minio presigned url in Seconds", javaType = "int")
    String PRESIGNED_URL_EXPIRATION_TIME = "CamelMinioPresignedURLExpirationTime";
}
