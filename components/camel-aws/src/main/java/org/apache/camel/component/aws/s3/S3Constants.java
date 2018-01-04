/**
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
package org.apache.camel.component.aws.s3;

/**
 * Constants used in Camel AWS S3 module
 * 
 */
public interface S3Constants {

    String BUCKET_NAME                     = "CamelAwsS3BucketName";
    String BUCKET_DESTINATION_NAME         = "CamelAwsS3BucketDestinationName";
    String CACHE_CONTROL                   = "CamelAwsS3ContentControl";
    String CONTENT_DISPOSITION             = "CamelAwsS3ContentDisposition";
    String CONTENT_ENCODING                = "CamelAwsS3ContentEncoding";
    String CONTENT_LENGTH                  = "CamelAwsS3ContentLength";
    String CONTENT_MD5                     = "CamelAwsS3ContentMD5";
    String CONTENT_TYPE                    = "CamelAwsS3ContentType";
    String DOWNLOAD_LINK_EXPIRATION        = "CamelAwsS3DownloadLinkExpiration";
    String DOWNLOAD_LINK                   = "CamelAwsS3DownloadLink";    
    String E_TAG                           = "CamelAwsS3ETag";
    String KEY                             = "CamelAwsS3Key";
    String DESTINATION_KEY                 = "CamelAwsS3DestinationKey";
    String LAST_MODIFIED                   = "CamelAwsS3LastModified";
    String STORAGE_CLASS                   = "CamelAwsS3StorageClass";
    String VERSION_ID                      = "CamelAwsS3VersionId";
    String CANNED_ACL                      = "CamelAwsS3CannedAcl";
    String ACL                             = "CamelAwsS3Acl";
    String USER_METADATA                   = "CamelAwsS3UserMetadata";
    String S3_HEADERS                      = "CamelAwsS3Headers";
    String S3_OPERATION                    = "CamelAwsS3Operation";
    String SERVER_SIDE_ENCRYPTION          = "CamelAwsS3ServerSideEncryption";
}