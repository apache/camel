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
package org.apache.camel.component.huaweicloud.obs.constants;

import org.apache.camel.Exchange;
import org.apache.camel.spi.Metadata;

/**
 * Constants for the exchange headers when consuming objects
 */
public final class OBSHeaders {
    @Metadata(label = "consumer", description = "Name of the bucket where object is contained", javaType = "String")
    public static final String BUCKET_NAME = "CamelHwCloudObsBucketName";
    @Metadata(label = "consumer", description = "The key that the object is stored under", javaType = "String")
    public static final String OBJECT_KEY = "CamelHwCloudObsObjectKey";
    @Metadata(label = "consumer", description = "The date and time that the object was last modified", javaType = "Date")
    public static final String LAST_MODIFIED = "CamelHwCloudObsLastModified";
    @Metadata(label = "consumer", description = "The 128-bit MD5 digest of the Base64 code of the object. " +
                                                "This data is the unique identifier of the object content",
              javaType = "String")
    public static final String ETAG = "CamelHwCloudObsETag";
    @Metadata(label = "consumer", description = "The 128-bit Base64-encoded digest used to decrypt the object",
              javaType = "String")
    public static final String CONTENT_MD5 = "CamelHwCloudObsContentMD5";
    @Metadata(label = "consumer", description = "Shows whether the object is a `file` or a `folder`", javaType = "String")
    public static final String OBJECT_TYPE = "CamelHwCloudObsObjectType";
    @Metadata(label = "consumer", description = "The size of the object body in bytes", javaType = "Long")
    public static final String CONTENT_LENGTH = Exchange.CONTENT_LENGTH;
    @Metadata(label = "consumer", description = "The type of content stored in the object", javaType = "String")
    public static final String CONTENT_TYPE = Exchange.CONTENT_TYPE;
    @Metadata(label = "consumer", description = "Name of the object with which the operation is to be performed",
              javaType = "String")
    public static final String FILE_NAME = Exchange.FILE_NAME;

    private OBSHeaders() {
    }
}
