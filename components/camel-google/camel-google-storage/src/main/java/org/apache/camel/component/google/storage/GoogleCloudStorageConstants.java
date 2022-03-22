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
package org.apache.camel.component.google.storage;

import org.apache.camel.Exchange;
import org.apache.camel.spi.Metadata;

public final class GoogleCloudStorageConstants {

    @Metadata(label = "producer", description = "The operation to perform.",
              javaType = "org.apache.camel.component.google.storage.GoogleCloudStorageOperations")
    public static final String OPERATION = "CamelGoogleCloudStorageOperation";
    @Metadata(label = "producer",
              description = "The bucket Name which this object will be stored or which will be used for the current operation",
              javaType = "String")
    public static final String BUCKET_NAME = "CamelGoogleCloudStorageBucketName";
    @Metadata(label = "producer", description = "The object Name which will be used for the current operation",
              javaType = "String")
    public static final String OBJECT_NAME = "CamelGoogleCloudStorageObjectName";
    @Metadata(label = "producer", description = "The object Destination Name which will be used for the current operation",
              javaType = "String")
    public static final String DESTINATION_OBJECT_NAME = "CamelGoogleCloudStorageDestinationObjectName";
    @Metadata(label = "producer", description = "The bucket Destination Name which will be used for the current operation",
              javaType = "String")
    public static final String DESTINATION_BUCKET_NAME = "CamelGoogleCloudStorageDestinationBucketName";
    @Metadata(label = "producer", description = "The time in millisecond the download link will be valid.", javaType = "Long",
              defaultValue = "300000")
    public static final String DOWNLOAD_LINK_EXPIRATION_TIME = "CamelGoogleCloudStorageDownloadLinkExpirationTime";
    @Metadata(description = "The content length of this object.", javaType = "Long")
    public static final String CONTENT_LENGTH = "CamelGoogleCloudStorageContentLength";
    @Metadata(description = "The content type of this object.", javaType = "String")
    public static final String CONTENT_TYPE = "CamelGoogleCloudStorageContentType";
    @Metadata(description = "The Cache-Control metadata can specify two different aspects of how data is served from Cloud Storage: "
                            +
                            "whether the data can be cached and whether the data can be transformed",
              javaType = "String")
    public static final String CACHE_CONTROL = "CamelGoogleCloudStorageCacheControl";
    @Metadata(description = "The content disposition of this object.", javaType = "String")
    public static final String CONTENT_DISPOSITION = "CamelGoogleCloudStorageContentDisposition";
    @Metadata(description = "The content encoding of this object.", javaType = "String")
    public static final String CONTENT_ENCODING = "CamelGoogleCloudStorageContentEncoding";
    @Metadata(description = "The md5 checksum of this object.", javaType = "String")
    public static final String CONTENT_MD5 = "CamelGoogleCloudStorageContentMd5";
    @Metadata(label = "consumer", description = "The name of the blob", javaType = "String")
    public static final String FILE_NAME = Exchange.FILE_NAME;
    @Metadata(label = "consumer", description = "The component count of this object", javaType = "Integer")
    public static final String METADATA_COMPONENT_COUNT = "CamelGoogleCloudStorageComponentCount";
    @Metadata(label = "consumer",
              description = "The Content-Language metadata indicates the language(s) that the object is intended for.",
              javaType = "String")
    public static final String METADATA_CONTENT_LANGUAGE = "CamelGoogleCloudStorageContentLanguage";
    @Metadata(label = "consumer", description = "The Custom-Time metadata is a user-specified date and time represented " +
                                                "in the RFC 3339 format YYYY-MM-DD'T'HH:MM:SS.SS'Z' or YYYY-MM-DD'T'HH:MM:SS'Z' when milliseconds are zero. "
                                                +
                                                "This metadata is typically set in order to use the DaysSinceCustomTime condition in Object Lifecycle Management.",
              javaType = "Long")
    public static final String METADATA_CUSTOM_TIME = "CamelGoogleCloudStorageCustomTime";
    @Metadata(label = "consumer", description = "The CRC32c of the object", javaType = "String")
    public static final String METADATA_CRC32C_HEX = "CamelGoogleCloudStorageCrc32cHex";
    @Metadata(description = "The ETag for the Object.", javaType = "String")
    public static final String METADATA_ETAG = "CamelGoogleCloudStorageETag";
    @Metadata(label = "consumer",
              description = "Is the generation number of the object for which you are retrieving information.",
              javaType = "Long")
    public static final String METADATA_GENERATION = "CamelGoogleCloudStorageGeneration";
    @Metadata(label = "consumer", description = "The blob id of the object", javaType = "com.google.cloud.storage.BlobId")
    public static final String METADATA_BLOB_ID = "CamelGoogleCloudStorageBlobId";
    @Metadata(label = "consumer", description = "The KMS key name", javaType = "String")
    public static final String METADATA_KMS_KEY_NAME = "CamelGoogleCloudStorageKmsKeyName";
    @Metadata(label = "consumer", description = "The media link", javaType = "String")
    public static final String METADATA_MEDIA_LINK = "CamelGoogleCloudStorageMediaLink";
    @Metadata(label = "consumer", description = "The metageneration of the object", javaType = "Long")
    public static final String METADATA_METAGENERATION = "CamelGoogleCloudStorageMetageneration";
    @Metadata(label = "consumer", description = "The storage class of the object",
              javaType = "com.google.cloud.storage.StorageClass")
    public static final String METADATA_STORAGE_CLASS = "CamelGoogleCloudStorageStorageClass";
    @Metadata(label = "consumer", description = "The creation time of the object", javaType = "Long")
    public static final String METADATA_CREATE_TIME = "CamelGoogleCloudStorageCreateTime";
    @Metadata(label = "consumer", description = "The last update of the object", javaType = "Date")
    public static final String METADATA_LAST_UPDATE = "CamelGoogleCloudStorageLastUpdate";

    /**
     * Prevent instantiation.
     */
    private GoogleCloudStorageConstants() {
    }
}
