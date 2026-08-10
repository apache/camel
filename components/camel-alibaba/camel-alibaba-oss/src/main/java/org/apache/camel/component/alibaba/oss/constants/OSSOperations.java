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
 * Constants for all the supported OSS operations
 */
public final class OSSOperations {
    public static final String LIST_BUCKETS = "listBuckets";
    public static final String LIST_OBJECTS = "listObjects";
    public static final String PUT_OBJECT = "putObject";
    public static final String GET_OBJECT = "getObject";
    public static final String DELETE_OBJECT = "deleteObject";
    public static final String COPY_OBJECT = "copyObject";
    public static final String HEAD_OBJECT = "headObject";

    private OSSOperations() {
    }
}
