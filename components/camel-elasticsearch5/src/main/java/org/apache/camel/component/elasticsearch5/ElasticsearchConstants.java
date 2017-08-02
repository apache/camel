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
    String PARENT = "parent";
    String TRANSPORT_ADDRESSES = "transportAddresses";
    String PROTOCOL = "elasticsearch";
    String IP = "ip";
    String PORT = "port";
    int    DEFAULT_PORT = 9300;
    int    DEFAULT_FOR_WAIT_ACTIVE_SHARDS = 1; // Meaning only wait for the primary shard
    String DEFAULT_PING_SCHEDULE = "5s"; // Meaning how often it should ping the cluster
    String DEFAULT_PING_TIMEOUT = "5s"; // Meaning how long to wait for ping before timeout
    String DEFAULT_TCP_CONNECT_TIMEOUT = "30s"; // Meaning how many seconds before it timeout when establish connection
    String TRANSPORT_ADDRESSES_SEPARATOR_REGEX = ",";
    String IP_PORT_SEPARATOR_REGEX = ":";
    String ES_QUERY_DSL_PREFIX = "query";
}
