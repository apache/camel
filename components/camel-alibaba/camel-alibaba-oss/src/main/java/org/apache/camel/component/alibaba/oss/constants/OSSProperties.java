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

/**
 * Constants for properties set on the exchange object
 */
public final class OSSProperties {
    public static final String OPERATION = "CamelAlibabaOssOperation";
    public static final String BUCKET_NAME = "CamelAlibabaOssBucketName";
    public static final String OBJECT_NAME = "CamelAlibabaOssObjectName";
    public static final String SOURCE_BUCKET_NAME = "CamelAlibabaOssSourceBucketName";
    public static final String SOURCE_OBJECT_NAME = "CamelAlibabaOssSourceObjectName";
    public static final String PREFIX = "CamelAlibabaOssPrefix";
    public static final String MAX_KEYS = "CamelAlibabaOssMaxKeys";

    private OSSProperties() {
    }
}
