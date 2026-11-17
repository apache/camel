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

public final class AlibabaSlsHeaders {

    @Metadata(label = "producer",
              description = "The operation that the producer performs. This header replaces the operation option. If"
                            + " this header is not set, an exchange property of the same name replaces the operation"
                            + " option.",
              javaType = "String")
    public static final String OPERATION = AlibabaSlsProperties.OPERATION;

    @Metadata(label = "producer",
              description = "The name of the project. This header replaces the project option. If this header is not"
                            + " set, an exchange property of the same name replaces the project option.",
              javaType = "String")
    public static final String PROJECT = AlibabaSlsProperties.PROJECT;

    @Metadata(label = "producer",
              description = "The name of the Logstore for putLogs and getLogs. The producer writes the logs to this"
                            + " Logstore for putLogs, and queries its logs for getLogs. This header replaces the"
                            + " logStoreName option. If this header is not set, an exchange property of the same name"
                            + " replaces the logStoreName option.",
              javaType = "String")
    public static final String LOG_STORE_NAME = AlibabaSlsProperties.LOG_STORE_NAME;

    @Metadata(label = "producer",
              description = "The query statement or analytic statement for getLogs. This header replaces the query"
                            + " option. If this header is not set, an exchange property of the same name replaces the"
                            + " query option.",
              javaType = "String")
    public static final String QUERY = AlibabaSlsProperties.QUERY;

    @Metadata(label = "producer",
              description = "The start time for getLogs, as a Unix timestamp in seconds. Simple Log Service requires a"
                            + " start time. This header replaces the from option. If this header is not set, an"
                            + " exchange property of the same name replaces the from option.",
              javaType = "Integer")
    public static final String FROM = AlibabaSlsProperties.FROM;

    @Metadata(label = "producer",
              description = "The end time for getLogs, as a Unix timestamp in seconds. Simple Log Service requires an"
                            + " end time. This header replaces the to option. If this header is not set, an exchange"
                            + " property of the same name replaces the to option.",
              javaType = "Integer")
    public static final String TO = AlibabaSlsProperties.TO;

    @Metadata(label = "producer",
              description = "The maximum number of logs to return for getLogs. Simple Log Service accepts a value of"
                            + " at most 100. Simple Log Service does not use it when query contains an analytic"
                            + " statement. This header replaces the line option. If this header is not set, an"
                            + " exchange property of the same name replaces the line option.",
              javaType = "Long")
    public static final String LINE = AlibabaSlsProperties.LINE;

    @Metadata(label = "producer",
              description = "The offset of the first log to return for getLogs. Simple Log Service does not use it"
                            + " when query contains an analytic statement. This header replaces the offset option. If"
                            + " this header is not set, an exchange property of the same name replaces the offset"
                            + " option.",
              javaType = "Long")
    public static final String OFFSET = AlibabaSlsProperties.OFFSET;

    @Metadata(label = "producer",
              description = "The log topic for getLogs. This header replaces the topic option. If this header is not"
                            + " set, an exchange property of the same name replaces the topic option.",
              javaType = "String")
    public static final String TOPIC = AlibabaSlsProperties.TOPIC;

    @Metadata(label = "producer",
              description = "Whether to return logs in descending order of log timestamps for getLogs. Simple Log"
                            + " Service does not use it when query contains an analytic statement. This header"
                            + " replaces the reverse option. If this header is not set, an exchange property of the"
                            + " same name replaces the reverse option.",
              javaType = "Boolean")
    public static final String REVERSE = AlibabaSlsProperties.REVERSE;

    @Metadata(label = "producer",
              description = "The name of the Logstore for listLogStores. Simple Log Service returns the Logstores"
                            + " whose name contains the value. If this header is not set, the producer uses an"
                            + " exchange property of the same name. If neither of the two is set, the producer adds no"
                            + " Logstore name to the request.",
              javaType = "String")
    public static final String LOGSTORE_NAME = AlibabaSlsProperties.LOGSTORE_NAME;

    @Metadata(label = "producer",
              description = "The type of the Logstore for listLogStores. The value must be standard or query. If this"
                            + " header is not set, the producer uses an exchange property of the same name. If neither"
                            + " of the two is set, the producer adds no Logstore type to the request.",
              javaType = "String")
    public static final String MODE = AlibabaSlsProperties.MODE;

    @Metadata(label = "producer",
              description = "The number of entries per page for listLogStores. Simple Log Service accepts a value of"
                            + " at most 500. If this header is not set, the producer uses an exchange property of the"
                            + " same name. If neither of the two is set, the producer adds no number of entries per"
                            + " page to the request.",
              javaType = "Integer")
    public static final String SIZE = AlibabaSlsProperties.SIZE;

    @Metadata(label = "producer",
              description = "The offset of the first Logstore to return for listLogStores. If this header is not set,"
                            + " the producer uses an exchange property of the same name. If neither of the two is set,"
                            + " the producer adds no offset to the request.",
              javaType = "Integer")
    public static final String LIST_OFFSET = AlibabaSlsProperties.LIST_OFFSET;

    @Metadata(label = "producer",
              description = "The telemetry type for listLogStores. The value must be None for logs or Metrics for"
                            + " metrics. If this header is not set, the producer uses an exchange property of the same"
                            + " name. If neither of the two is set, the producer adds no telemetry type to the"
                            + " request.",
              javaType = "String")
    public static final String TELEMETRY_TYPE = AlibabaSlsProperties.TELEMETRY_TYPE;

    @Metadata(label = "producer",
              description = "The request ID that Simple Log Service returns in the response to the operation. The"
                            + " producer sets it, unless the exchange fails.",
              javaType = "String")
    public static final String REQUEST_ID = AlibabaSlsProperties.REQUEST_ID;

    @Metadata(label = "producer",
              description = "The HTTP status code of the response to the operation. The producer sets it, unless the"
                            + " exchange fails.",
              javaType = "Integer")
    public static final String STATUS_CODE = AlibabaSlsProperties.STATUS_CODE;

    private AlibabaSlsHeaders() {
    }
}
