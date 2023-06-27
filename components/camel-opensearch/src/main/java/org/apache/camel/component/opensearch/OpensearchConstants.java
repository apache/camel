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
package org.apache.camel.component.opensearch;

import org.apache.camel.spi.Metadata;

public interface OpensearchConstants {

    @Metadata(description = "The operation to perform",
              javaType = "org.apache.camel.component.opensearch.OpensearchOperation")
    String PARAM_OPERATION = "operation";
    @Metadata(description = "The id of the indexed document.", javaType = "String")
    String PARAM_INDEX_ID = "indexId";
    @Metadata(description = "The name of the index to act against", javaType = "String")
    String PARAM_INDEX_NAME = "indexName";
    @Metadata(description = "The full qualified name of the class of the document to unmarshall", javaType = "Class",
              defaultValue = "ObjectNode")
    String PARAM_DOCUMENT_CLASS = "documentClass";
    @Metadata(description = "The index creation waits for the write consistency number of shards to be available",
              javaType = "Integer")
    String PARAM_WAIT_FOR_ACTIVE_SHARDS = "waitForActiveShards";
    @Metadata(description = "The starting index of the response.", javaType = "Integer")
    String PARAM_SCROLL_KEEP_ALIVE_MS = "scrollKeepAliveMs";
    @Metadata(description = "Set to true to enable scroll usage", javaType = "Boolean")
    String PARAM_SCROLL = "useScroll";
    @Metadata(description = "The size of the response.", javaType = "Integer")
    String PARAM_SIZE = "size";
    @Metadata(description = "The starting index of the response.", javaType = "Integer")
    String PARAM_FROM = "from";

    String PROPERTY_SCROLL_OPENSEARCH_QUERY_COUNT = "CamelOpenSearchScrollQueryCount";

    int DEFAULT_PORT = 9200;
    int DEFAULT_FOR_WAIT_ACTIVE_SHARDS = 1; // Meaning only wait for the primary shard
    int DEFAULT_SOCKET_TIMEOUT = 30000; // Meaning how long time to wait before the socket timeout
    int MAX_RETRY_TIMEOUT = 30000; // Meaning how long to wait before retry again
    int DEFAULT_CONNECTION_TIMEOUT = 30000; // Meaning how many seconds before it timeouts when establish connection
    int DEFAULT_SNIFFER_INTERVAL = 60000 * 5; // Meaning how often it should search for OpenSearch nodes
    int DEFAULT_AFTER_FAILURE_DELAY = 60000; // Meaning when should the sniff execution scheduled after a failure
    int DEFAULT_SCROLL_KEEP_ALIVE_MS = 60000; // Meaning how many milliseconds OpenSearch will keep the search context
}
