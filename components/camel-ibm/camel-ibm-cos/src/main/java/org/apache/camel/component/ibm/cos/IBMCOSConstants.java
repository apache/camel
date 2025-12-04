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

package org.apache.camel.component.ibm.cos;

import org.apache.camel.spi.Metadata;

/**
 * Constants used in Camel IBM COS module
 */
public interface IBMCOSConstants {

    @Metadata(
            description =
                    "The bucket Name which this object will be stored or which will be used for the current operation",
            javaType = "String")
    String BUCKET_NAME = "CamelIBMCOSBucketName";

    @Metadata(
            label = "producer",
            description = "The bucket Destination Name which will be used for the current operation",
            javaType = "String")
    String BUCKET_DESTINATION_NAME = "CamelIBMCOSBucketDestinationName";

    @Metadata(
            description = "The *optional* Cache-Control HTTP header which allows the user to specify caching behavior",
            javaType = "String")
    String CACHE_CONTROL = "CamelIBMCOSContentControl";

    @Metadata(
            description =
                    "The *optional* Content-Disposition HTTP header, which specifies presentational information such as the recommended filename",
            javaType = "String")
    String CONTENT_DISPOSITION = "CamelIBMCOSContentDisposition";

    @Metadata(
            description =
                    "The *optional* Content-Encoding HTTP header specifying what content encodings have been applied to the object",
            javaType = "String")
    String CONTENT_ENCODING = "CamelIBMCOSContentEncoding";

    @Metadata(
            description = "The Content-Length HTTP header indicating the size of the associated object in bytes",
            javaType = "Long")
    String CONTENT_LENGTH = "CamelIBMCOSContentLength";

    @Metadata(description = "The base64 encoded 128-bit MD5 digest of the associated object", javaType = "String")
    String CONTENT_MD5 = "CamelIBMCOSContentMD5";

    @Metadata(
            description =
                    "The Content-Type HTTP header, which indicates the type of content stored in the associated object",
            javaType = "String")
    String CONTENT_TYPE = "CamelIBMCOSContentType";

    @Metadata(description = "The ETag value for the object", javaType = "String")
    String E_TAG = "CamelIBMCOSETag";

    @Metadata(description = "The key under which this object is stored or will be stored", javaType = "String")
    String KEY = "CamelIBMCOSKey";

    @Metadata(
            label = "producer",
            description = "The Destination key which will be used for the current operation",
            javaType = "String")
    String DESTINATION_KEY = "CamelIBMCOSDestinationKey";

    @Metadata(
            description =
                    "The value of the Last-Modified header, indicating the date and time at which IBM COS last recorded a modification to the object",
            javaType = "java.util.Date")
    String LAST_MODIFIED = "CamelIBMCOSLastModified";

    @Metadata(description = "The version ID of the associated IBM COS object if available", javaType = "String")
    String VERSION_ID = "CamelIBMCOSVersionId";

    @Metadata(description = "The operation to perform", javaType = "String")
    String COS_OPERATION = "CamelIBMCOSOperation";

    @Metadata(description = "The prefix which is used to filter objects", javaType = "String")
    String PREFIX = "CamelIBMCOSPrefix";

    @Metadata(description = "The delimiter which is used to filter objects", javaType = "String")
    String DELIMITER = "CamelIBMCOSDelimiter";

    @Metadata(
            label = "producer",
            description = "A list of keys to delete when using deleteObjects operation",
            javaType = "java.util.List<String>")
    String KEYS_TO_DELETE = "CamelIBMCOSKeysToDelete";

    @Metadata(
            description = "A map of metadata to be stored with the object in IBM COS",
            javaType = "java.util.Map<String, String>")
    String METADATA = "CamelIBMCOSMetadata";

    @Metadata(
            label = "producer",
            description = "The range start position for partial object retrieval",
            javaType = "Long")
    String RANGE_START = "CamelIBMCOSRangeStart";

    @Metadata(
            label = "producer",
            description = "The range end position for partial object retrieval",
            javaType = "Long")
    String RANGE_END = "CamelIBMCOSRangeEnd";

    @Metadata(label = "producer", description = "Whether the bucket exists or not", javaType = "Boolean")
    String BUCKET_EXISTS = "CamelIBMCOSBucketExists";
}
