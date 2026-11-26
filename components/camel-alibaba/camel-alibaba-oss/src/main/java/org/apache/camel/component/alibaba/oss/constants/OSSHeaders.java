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
package org.apache.camel.component.alibaba.oss.constants;

import org.apache.camel.Exchange;
import org.apache.camel.spi.Metadata;

/**
 * Constants for the message headers of the consumer and the producer
 */
public final class OSSHeaders {
    @Metadata(label = "producer", description = "The operation that the producer performs. An exchange property of the same"
                                                + " name replaces the operation option, and this header replaces both.",
              javaType = "String", enums = "listBuckets,listObjects,putObject,getObject,deleteObject,copyObject,headObject")
    public static final String OPERATION = OSSProperties.OPERATION;
    @Metadata(label = "common", description = "The name of the bucket. The consumer sets it for a consumed object, and the"
                                              + " producer for getObject. An exchange property of the same name replaces the"
                                              + " bucketName option for the producer, and this header replaces both.",
              javaType = "String")
    public static final String BUCKET_NAME = "CamelAlibabaOssBucketName";
    @Metadata(label = "producer", description = "The name of the object, or of the destination object for copyObject. An"
                                                + " exchange property of the same name replaces the objectName option, and this"
                                                + " header replaces both.",
              javaType = "String")
    public static final String OBJECT_NAME = OSSProperties.OBJECT_NAME;
    @Metadata(label = "producer", description = "The name of the source bucket for copyObject, which requires it. If this"
                                                + " header is not set, the producer uses an exchange property of the same"
                                                + " name.",
              javaType = "String")
    public static final String SOURCE_BUCKET_NAME = OSSProperties.SOURCE_BUCKET_NAME;
    @Metadata(label = "producer", description = "The name of the source object for copyObject, which requires it. If this"
                                                + " header is not set, the producer uses an exchange property of the same"
                                                + " name.",
              javaType = "String")
    public static final String SOURCE_OBJECT_NAME = OSSProperties.SOURCE_OBJECT_NAME;
    @Metadata(label = "producer", description = "The prefix of the names of the objects that listObjects returns. An exchange"
                                                + " property of the same name replaces the prefix option for the producer, and"
                                                + " this header replaces both.",
              javaType = "String")
    public static final String PREFIX = OSSProperties.PREFIX;
    @Metadata(label = "producer", description = "The maximum number of objects that listObjects returns. An exchange property"
                                                + " of the same name replaces the maxKeys option, and this header replaces"
                                                + " both.",
              javaType = "Integer")
    public static final String MAX_KEYS = OSSProperties.MAX_KEYS;
    @Metadata(label = "common", description = "The key that the object is stored under", javaType = "String")
    public static final String OBJECT_KEY = "CamelAlibabaOssObjectKey";
    @Metadata(label = "common", description = "The date and time that the object was last modified", javaType = "String")
    public static final String LAST_MODIFIED = "CamelAlibabaOssLastModified";
    @Metadata(label = "common", description = "The entity tag (ETag) of the object, which identifies its content. It is the MD5"
                                              + " hash of the content only for an object created by PutObject.",
              javaType = "String")
    public static final String ETAG = "CamelAlibabaOssETag";
    @Metadata(label = "common", description = "The 128-bit Base64-encoded digest of the object", javaType = "String")
    public static final String CONTENT_MD5 = "CamelAlibabaOssContentMD5";
    @Metadata(label = "common", description = "Shows whether the object is a `file` or a `folder`", javaType = "String")
    public static final String OBJECT_TYPE = "CamelAlibabaOssObjectType";
    @Metadata(label = "common", description = "The size of the object body in bytes", javaType = "Long")
    public static final String CONTENT_LENGTH = "CamelAlibabaOssContentLength";
    @Metadata(label = "common", description = "The type of content stored in the object", javaType = "String")
    public static final String CONTENT_TYPE = "CamelAlibabaOssContentType";
    @Metadata(label = "common", description = "The name of the object. The consumer sets it for a consumed object, and the"
                                              + " producer for getObject.",
              javaType = "String")
    public static final String FILE_NAME = Exchange.FILE_NAME;

    private OSSHeaders() {
    }
}
