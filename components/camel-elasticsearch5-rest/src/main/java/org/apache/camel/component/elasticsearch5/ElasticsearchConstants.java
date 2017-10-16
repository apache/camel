/**
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
package org.apache.camel.component.elasticsearch5;

public interface ElasticsearchConstants {

    String PARAM_OPERATION = "operation";
    String PARAM_INDEX_ID = "indexId";
    String PARAM_INDEX_NAME = "indexName";
    String PARAM_INDEX_TYPE = "indexType";
    String PARAM_WAIT_FOR_ACTIVE_SHARDS = "waitForActiveShards";

    int    DEFAULT_PORT = 9200;
    int    DEFAULT_FOR_WAIT_ACTIVE_SHARDS = 1; // Meaning only wait for the primary shard
    int    DEFAULT_SOCKET_TIMEOUT = 30000; // Meaning how long time to wait before the socket timeout
    int    MAX_RETRY_TIMEOUT = 30000; // Meaning how long to wait before retry again
    int    DEFAULT_CONNECTION_TIMEOUT = 30000; // Meaning how many seconds before it timeout when establish connection
    int    DEFAULT_SNIFFER_INTERVAL = 60000 * 5; // Meaning how often it should search for elasticsearch nodes
    int    DEFAULT_AFTER_FAILURE_DELAY = 60000; // Meaning when should the sniff execution scheduled after a failure

}
