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
package org.apache.camel.component.alibaba.fc.constants;

import org.apache.camel.spi.Metadata;

public final class FCHeaders {

    @Metadata(label = "producer", description = "FC service name override", javaType = "String")
    public static final String SERVICE_NAME = FCProperties.SERVICE_NAME;

    @Metadata(label = "producer", description = "FC function name override", javaType = "String")
    public static final String FUNCTION_NAME = FCProperties.FUNCTION_NAME;

    @Metadata(label = "producer", description = "Function qualifier override", javaType = "String")
    public static final String QUALIFIER = FCProperties.QUALIFIER;

    @Metadata(label = "producer", description = "Alibaba Cloud request id", javaType = "String")
    public static final String REQUEST_ID = FCProperties.REQUEST_ID;

    @Metadata(label = "producer", description = "HTTP status code from FC invoke response", javaType = "Integer")
    public static final String STATUS_CODE = "CamelAlibabaFcStatusCode";

    private FCHeaders() {
    }
}
