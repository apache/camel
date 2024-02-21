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
package org.apache.camel.component.aws2.s3;

import org.apache.camel.Exchange;
import org.apache.camel.spi.Metadata;

/**
 * Constants used in Camel AWS2 S3 module
 */
public interface AWS2S3Constants {

    @Metadata(description = "The bucket Name which this object will be stored or which will be used for the current operation or in which this object is contained.",
              javaType = "String")
    String BUCKET_NAME = "CamelAwsS3BucketName";
    @Metadata(label = "producer", description = "The bucket Destination Name which will be used for the current operation",
              javaType = "String")
    String BUCKET_DESTINATION_NAME = "CamelAwsS3BucketDestinationName";
    @Metadata(description = "The *optional* Cache-Control HTTP header which allows the user to\n" +
                            "specify caching behavior along the HTTP request/reply chain.",
              javaType = "String")
    String CACHE_CONTROL = "CamelAwsS3ContentControl";
    @Metadata(description = "The *optional* Content-Disposition HTTP header, which specifies\n" +
                            "presentational information such as the recommended filename for the\n" +
                            "object to be saved as.",
              javaType = "String")
    String CONTENT_DISPOSITION = "CamelAwsS3ContentDisposition";
    @Metadata(description = "The *optional* Content-Encoding HTTP header specifying what content\n" +
                            "encodings have been applied to the object and what decoding mechanisms\n" +
                            "must be applied in order to obtain the media-type referenced by the\n" +
                            "Content-Type field.",
              javaType = "String")
    String CONTENT_ENCODING = "CamelAwsS3ContentEncoding";
    @Metadata(description = "The Content-Length HTTP header indicating the size of the associated\n" +
                            "object in bytes.",
              javaType = "Long")
    String CONTENT_LENGTH = "CamelAwsS3ContentLength";
    @Metadata(description = "The base64 encoded 128-bit MD5 digest of the associated object (content\n" +
                            "- not including headers) according to RFC 1864. This data is used as a\n" +
                            "message integrity check to verify that the data received by Amazon S3 is\n" +
                            "the same data that the caller sent.",
              javaType = "String")
    String CONTENT_MD5 = "CamelAwsS3ContentMD5";
    @Metadata(description = "The Content-Type HTTP header, which indicates the type of content stored\n" +
                            "in the associated object. The value of this header is a standard MIME\n" +
                            "type.",
              javaType = "String")
    String CONTENT_TYPE = "CamelAwsS3ContentType";
    @Metadata(description = "(producer) The ETag value for the newly uploaded object.\n" +
                            "(consumer) The hex encoded 128-bit MD5 digest of the associated object according to\n" +
                            "RFC 1864. This data is used as an integrity check to verify that the\n" +
                            "data received by the caller is the same data that was sent by Amazon S3.",
              javaType = "String")
    String E_TAG = "CamelAwsS3ETag";
    @Metadata(description = "The key under which this object is stored or will be stored or which will be used for the current operation",
              javaType = "String")
    String KEY = "CamelAwsS3Key";
    @Metadata(label = "producer", description = "The Destination key which will be used for the current operation",
              javaType = "String")
    String DESTINATION_KEY = "CamelAwsS3DestinationKey";
    @Metadata(description = "The value of the Last-Modified header, indicating the date and time at\n" +
                            "which Amazon S3 last recorded a modification to the associated object.",
              javaType = "Date")
    String LAST_MODIFIED = "CamelAwsS3LastModified";
    @Metadata(description = "The storage class of this object.", javaType = "String")
    String STORAGE_CLASS = "CamelAwsS3StorageClass";
    @Metadata(description = "(producer) The *optional* version ID of the newly uploaded object.\n" +
                            "(consumer) The version ID of the associated Amazon S3 object if available. Version\n" +
                            "IDs are only assigned to objects when an object is uploaded to an Amazon\n" +
                            "S3 bucket that has object versioning enabled.",
              javaType = "String")
    String VERSION_ID = "CamelAwsS3VersionId";
    @Metadata(label = "producer", description = "The canned acl that will be applied to the object. see\n" +
                                                "`software.amazon.awssdk.services.s3.model.ObjectCannedACL` for allowed\n" +
                                                "values.",
              javaType = "String")
    String CANNED_ACL = "CamelAwsS3CannedAcl";
    @Metadata(label = "producer", description = "A well constructed Amazon S3 Access Control List object.",
              javaType = "software.amazon.awssdk.services.s3.model.BucketCannedACL")
    String ACL = "CamelAwsS3Acl";
    @Metadata(description = "The operation to perform. Permitted values are copyObject, deleteObject, listBuckets, deleteBucket, listObjects",
              javaType = "String")
    String S3_OPERATION = "CamelAwsS3Operation";
    @Metadata(description = "Sets the server-side encryption algorithm when encrypting\n" +
                            "the object using AWS-managed keys. For example use AES256.",
              javaType = "String")
    String SERVER_SIDE_ENCRYPTION = "CamelAwsS3ServerSideEncryption";
    @Metadata(label = "consumer",
              description = "If the object expiration is configured (see PUT Bucket lifecycle), the response includes this header.",
              javaType = "String")
    String EXPIRATION_TIME = "CamelAwsS3ExpirationTime";
    @Metadata(label = "consumer",
              description = "Amazon S3 can return this if your request involves a bucket that is either a source or destination in a replication rule.",
              javaType = "software.amazon.awssdk.services.s3.model.ReplicationStatus")
    String REPLICATION_STATUS = "CamelAwsS3ReplicationStatus";
    @Metadata(label = "producer", description = "The position of the first byte to get", javaType = "String")
    String RANGE_START = "CamelAwsS3RangeStart";
    @Metadata(label = "producer", description = "The position of the last byte to get", javaType = "String")
    String RANGE_END = "CamelAwsS3RangeEnd";
    @Metadata(label = "producer", description = "The expiration time of the download link in milliseconds", javaType = "Long")
    String DOWNLOAD_LINK_EXPIRATION_TIME = "CamelAwsS3DowloadLinkExpirationTime";
    @Metadata(label = "producer", description = "Whether the download link is browser compatible", javaType = "boolean")
    String DOWNLOAD_LINK_BROWSER_COMPATIBLE = "CamelAwsS3DownloadLinkBrowserCompatible";
    @Metadata(label = "producer",
              description = "The headers that are needed by the service (not needed when BrowserCompatible is true)",
              javaType = "Map<String, List<String>>")
    String DOWNLOAD_LINK_HTTP_REQUEST_HEADERS = "CamelAwsS3DownloadLinkHttpRequestHeaders";
    @Metadata(label = "producer",
              description = "The request payload that is needed by the service (not needed when BrowserCompatible is true)",
              javaType = "String")
    String DOWNLOAD_LINK_SIGNED_PAYLOAD = "CamelAwsS3DownloadLinkSignedPayload";
    @Metadata(description = "A map of metadata to be stored or stored with the object in S3. More details about\n" +
                            "metadata https://docs.aws.amazon.com/AmazonS3/latest/userguide/UsingMetadata.html[here].",
              javaType = "Map<String, String>")
    String METADATA = "CamelAwsS3Metadata";
    @Metadata(label = "consumer", description = "The timestamp of the message", javaType = "long")
    String MESSAGE_TIMESTAMP = Exchange.MESSAGE_TIMESTAMP;

    @Metadata(description = "The prefix which is used in the com.amazonaws.services.s3.model.ListObjectsRequest to only list objects we are interested in")
    String PREFIX = "CamelAwsS3Prefix";
    @Metadata(description = "The delimiter which is used in the com.amazonaws.services.s3.model.ListObjectsRequest to only list objects we are interested in",
              javaType = "String")
    String DELIMITER = "CamelAwsS3Delimiter";
}
