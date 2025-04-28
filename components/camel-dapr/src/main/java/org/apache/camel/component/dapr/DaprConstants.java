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
package org.apache.camel.component.dapr;

import org.apache.camel.spi.Metadata;

public class DaprConstants {

    private static final String HEADER_PREFIX = "CamelDapr";

    @Metadata(label = "producer", description = "Target service to invoke. Can be a Dapr App ID, a named HTTPEndpoint, " +
                                                "or a FQDN/public URL",
              javaType = "String")
    public static final String SERVICE_TO_INVOKE = HEADER_PREFIX + "ServiceToInvoke";
    @Metadata(label = "producer", description = "The name of the method or route to invoke on the target service",
              javaType = "String")
    public static final String METHOD_TO_INVOKE = HEADER_PREFIX + "MethodToInvoke";
    @Metadata(label = "producer", description = "The HTTP verb to use for service invocation", javaType = "String")
    public static final String VERB = HEADER_PREFIX + "Verb";
    @Metadata(label = "producer", description = "The query parameters for HTTP requests",
              javaType = "Map<String, List<String>>")
    public static final String QUERY_PARAMETERS = HEADER_PREFIX + "QueryParameters";
    @Metadata(label = "producer", description = "The headers for HTTP requests", javaType = "Map<String, String>")
    public static final String HTTP_HEADERS = HEADER_PREFIX + "HttpHeaders";
    @Metadata(label = "producer", description = "The HttpExtension object for service invocation. Takes precedence over verb",
              javaType = "HttpExtension")
    public static final String HTTP_EXTENSION = HEADER_PREFIX + "HttpExtension";
    @Metadata(label = "producer", description = "The state operation to perform on the state store. " +
                                                "Required for DaprOperation.state operation",
              javaType = "StateOperation", defaultValue = "get",
              enums = "save, saveBulk, get, getBulk, delete, executeTransaction")
    public static final String STATE_OPERATION = HEADER_PREFIX + "StateOperation";
    @Metadata(label = "producer",
              description = "The name of the Dapr state store to interact with, defined in statestore.yaml config",
              javaType = "String")
    public static final String STATE_STORE = HEADER_PREFIX + "StateStore";
    @Metadata(label = "producer", description = "The key used to identify the state object within the specified state store",
              javaType = "String")
    public static final String KEY = HEADER_PREFIX + "Key";
    @Metadata(label = "producer", description = "The eTag for optimistic concurrency during state save or delete operations",
              javaType = "String")
    public static final String E_TAG = HEADER_PREFIX + "ETag";
    @Metadata(label = "producer", description = "Concurrency mode to use with state operations",
              javaType = "io.dapr.client.domain.StateOptions.Concurrency")
    public static final String CONCURRENCY = HEADER_PREFIX + "Concurrency";
    @Metadata(label = "producer", description = "Consistency level to use with state operations",
              javaType = "io.dapr.client.domain.StateOptions.Consistency")
    public static final String CONSISTENCY = HEADER_PREFIX + "Consistency";
    @Metadata(label = "producer", description = "Additional key-value pairs to be passed to the state store",
              javaType = "Map<String, String>")
    public static final String METADATA = HEADER_PREFIX + "Metadata";
    @Metadata(label = "producer", description = "List of states for bulk save operation", javaType = "List<State<?>>")
    public static final String STATES = HEADER_PREFIX + "States";
    @Metadata(label = "producer", description = "List of keys for bulk get operation", javaType = "List<String>")
    public static final String KEYS = HEADER_PREFIX + "Keys";
    @Metadata(label = "producer", description = "List of transactions for execute transactions state operations",
              javaType = "List<TransactionalStateOperation<?>>")
    public static final String TRANSACTIONS = HEADER_PREFIX + "Transactions";
}
