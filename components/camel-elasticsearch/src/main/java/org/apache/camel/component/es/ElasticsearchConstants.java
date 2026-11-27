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
package org.apache.camel.component.es;

import org.apache.camel.spi.Metadata;

public interface ElasticsearchConstants {

    @Metadata(description = "The operation that the producer performs. It replaces the operation option. A request of the"
                            + " Elasticsearch Java API Client in the body sets the operation by its type and replaces both.",
              javaType = "org.apache.camel.component.es.ElasticsearchOperation")
    String PARAM_OPERATION = "CamelElasticsearchOperation";
    @Metadata(description = "The id of the document for Index and Update. If it is not set for Index, Elasticsearch"
                            + " generates the id.",
              javaType = "String")
    String PARAM_INDEX_ID = "CamelElasticsearchIndexId";
    @Metadata(description = "The name of the index that the operation acts on. It replaces the indexName option.",
              javaType = "String")
    String PARAM_INDEX_NAME = "CamelElasticsearchIndexName";
    @Metadata(description = "The class to which the documents in the responses of GetById, MultiGet, Search and MultiSearch"
                            + " are deserialized. It replaces the documentClass option.",
              javaType = "Class", defaultValue = "ObjectNode")
    String PARAM_DOCUMENT_CLASS = "CamelElasticsearchDocumentClass";
    @Metadata(description = "The number of active shard copies that Index and Update wait for before they write. It replaces"
                            + " the waitForActiveShards option.",
              javaType = "Integer")
    String PARAM_WAIT_FOR_ACTIVE_SHARDS = "CamelElasticsearchWaitForActiveShards";
    @Metadata(description = "The time in milliseconds that Elasticsearch keeps the search context of a scroll alive between"
                            + " two scroll requests. It replaces the scrollKeepAliveMs option.",
              javaType = "Integer")
    String PARAM_SCROLL_KEEP_ALIVE_MS = "CamelElasticsearchScrollKeepAliveMs";
    @Metadata(description = "Whether Search reads all the hits with the scroll API. It replaces the useScroll option.",
              javaType = "Boolean")
    String PARAM_SCROLL = "CamelElasticsearchUseScroll";
    @Metadata(description = "The maximum number of hits that Search returns when the body is JSON text or a Map. It replaces"
                            + " the size option.",
              javaType = "Integer")
    String PARAM_SIZE = "CamelElasticsearchSize";
    @Metadata(description = "The offset of the first hit that Search returns when the body is JSON text or a Map. It replaces"
                            + " the from option.",
              javaType = "Integer")
    String PARAM_FROM = "CamelElasticsearchFrom";
    @Metadata(description = "Whether the body of Update is only the partial document to merge into the document. It replaces"
                            + " the enableDocumentOnlyMode option.",
              javaType = "Boolean", defaultValue = "false")
    String PARAM_DOCUMENT_MODE = "CamelElasticsearchEnableDocumentOnlyMode";

    String PROPERTY_SCROLL_ES_QUERY_COUNT = "CamelElasticsearchScrollQueryCount";

    int DEFAULT_PORT = 9200;
    int DEFAULT_FOR_WAIT_ACTIVE_SHARDS = 1; // Meaning only wait for the primary shard
    int DEFAULT_SOCKET_TIMEOUT = 30000; // Meaning how long time to wait before the socket timeout
    int MAX_RETRY_TIMEOUT = 30000; // Meaning how long to wait before retry again
    int DEFAULT_CONNECTION_TIMEOUT = 30000; // Meaning how many seconds before it timeout when establish connection
    int DEFAULT_SNIFFER_INTERVAL = 60000 * 5; // Meaning how often it should search for elasticsearch nodes
    int DEFAULT_AFTER_FAILURE_DELAY = 60000; // Meaning when should the sniff execution scheduled after a failure
    int DEFAULT_SCROLL_KEEP_ALIVE_MS = 60000; // Meaning how many milliseconds elasticsearch will keep the search context
}
