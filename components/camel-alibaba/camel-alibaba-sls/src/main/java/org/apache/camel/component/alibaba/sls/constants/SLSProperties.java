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

public final class SLSProperties {

    @Metadata(label = "producer", description = "Operation to perform", javaType = "String")
    public static final String OPERATION = "CamelAlibabaSlsOperation";

    @Metadata(label = "producer", description = "SLS project name override", javaType = "String")
    public static final String PROJECT = "CamelAlibabaSlsProject";

    @Metadata(label = "producer", description = "SLS log store name override", javaType = "String")
    public static final String LOG_STORE_NAME = "CamelAlibabaSlsLogStoreName";

    @Metadata(label = "producer", description = "Log query string override", javaType = "String")
    public static final String QUERY = "CamelAlibabaSlsQuery";

    @Metadata(label = "producer", description = "Query start time override (Unix timestamp in seconds)", javaType = "Integer")
    public static final String FROM = "CamelAlibabaSlsFrom";

    @Metadata(label = "producer", description = "Query end time override (Unix timestamp in seconds)", javaType = "Integer")
    public static final String TO = "CamelAlibabaSlsTo";

    @Metadata(label = "producer", description = "Maximum number of log lines to return", javaType = "Long")
    public static final String LINE = "CamelAlibabaSlsLine";

    @Metadata(label = "producer", description = "Log query offset", javaType = "Long")
    public static final String OFFSET = "CamelAlibabaSlsOffset";

    @Metadata(label = "producer", description = "Log topic filter override", javaType = "String")
    public static final String TOPIC = "CamelAlibabaSlsTopic";

    @Metadata(label = "producer", description = "Whether to return logs in reverse order", javaType = "Boolean")
    public static final String REVERSE = "CamelAlibabaSlsReverse";

    @Metadata(label = "producer", description = "Log store name prefix filter for listLogStores", javaType = "String")
    public static final String LOGSTORE_NAME = "CamelAlibabaSlsLogstoreName";

    @Metadata(label = "producer", description = "List mode for listLogStores", javaType = "String")
    public static final String MODE = "CamelAlibabaSlsMode";

    @Metadata(label = "producer", description = "Page size for listLogStores", javaType = "Integer")
    public static final String SIZE = "CamelAlibabaSlsSize";

    @Metadata(label = "producer", description = "Telemetry type filter for listLogStores", javaType = "String")
    public static final String TELEMETRY_TYPE = "CamelAlibabaSlsTelemetryType";

    @Metadata(label = "producer", description = "Request id returned by SLS", javaType = "String")
    public static final String REQUEST_ID = "CamelAlibabaSlsRequestId";

    private SLSProperties() {
    }
}
