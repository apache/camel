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
package org.apache.camel.component.alibaba.sls.constants;

import org.apache.camel.spi.Metadata;

public final class SLSHeaders {

    @Metadata(label = "producer", description = "SLS project name override", javaType = "String")
    public static final String PROJECT = SLSProperties.PROJECT;

    @Metadata(label = "producer", description = "SLS log store name override", javaType = "String")
    public static final String LOG_STORE_NAME = SLSProperties.LOG_STORE_NAME;

    @Metadata(label = "producer", description = "Log query string override", javaType = "String")
    public static final String QUERY = SLSProperties.QUERY;

    @Metadata(label = "producer", description = "Query start time override (Unix timestamp in seconds)", javaType = "Integer")
    public static final String FROM = SLSProperties.FROM;

    @Metadata(label = "producer", description = "Query end time override (Unix timestamp in seconds)", javaType = "Integer")
    public static final String TO = SLSProperties.TO;

    @Metadata(label = "producer", description = "Maximum number of log lines to return", javaType = "Long")
    public static final String LINE = SLSProperties.LINE;

    @Metadata(label = "producer", description = "Log query offset", javaType = "Long")
    public static final String OFFSET = SLSProperties.OFFSET;

    @Metadata(label = "producer", description = "Log topic filter override", javaType = "String")
    public static final String TOPIC = SLSProperties.TOPIC;

    @Metadata(label = "producer", description = "Whether to return logs in reverse order", javaType = "Boolean")
    public static final String REVERSE = SLSProperties.REVERSE;

    @Metadata(label = "producer", description = "Log store name prefix filter for listLogStores", javaType = "String")
    public static final String LOGSTORE_NAME = SLSProperties.LOGSTORE_NAME;

    @Metadata(label = "producer", description = "List mode for listLogStores", javaType = "String")
    public static final String MODE = SLSProperties.MODE;

    @Metadata(label = "producer", description = "Page size for listLogStores", javaType = "Integer")
    public static final String SIZE = SLSProperties.SIZE;

    @Metadata(label = "producer", description = "Telemetry type filter for listLogStores", javaType = "String")
    public static final String TELEMETRY_TYPE = SLSProperties.TELEMETRY_TYPE;

    @Metadata(label = "producer", description = "Alibaba Cloud request id", javaType = "String")
    public static final String REQUEST_ID = SLSProperties.REQUEST_ID;

    @Metadata(label = "producer", description = "HTTP status code from SLS response", javaType = "Integer")
    public static final String STATUS_CODE = "CamelAlibabaSlsStatusCode";

    private SLSHeaders() {
    }
}
