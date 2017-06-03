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

package org.apache.camel.component.elasticsearch;

import org.elasticsearch.action.WriteConsistencyLevel;

public interface ElasticsearchConstants {

    String PARAM_OPERATION = "operation";
    String OPERATION_INDEX = "INDEX";
    String OPERATION_UPDATE = "UPDATE";
    String OPERATION_BULK = "BULK";
    String OPERATION_BULK_INDEX = "BULK_INDEX";
    String OPERATION_GET_BY_ID = "GET_BY_ID";
    String OPERATION_MULTIGET = "MULTIGET";
    String OPERATION_DELETE = "DELETE";
    String OPERATION_DELETE_INDEX = "DELETE_INDEX";
    String OPERATION_SEARCH = "SEARCH";
    String OPERATION_MULTISEARCH = "MULTISEARCH";
    String OPERATION_EXISTS = "EXISTS";
    String PARAM_INDEX_ID = "indexId";
    String PARAM_DATA = "data";
    String PARAM_INDEX_NAME = "indexName";
    String PARAM_INDEX_TYPE = "indexType";
    String PARAM_CONSISTENCY_LEVEL = "consistencyLevel";
    String PARENT = "parent";
    String TRANSPORT_ADDRESSES = "transportAddresses";
    String PROTOCOL = "elasticsearch";
    String LOCAL_NAME = "local";
    String IP = "ip";
    String PORT = "port";
    Integer DEFAULT_PORT = 9300;
    WriteConsistencyLevel DEFAULT_CONSISTENCY_LEVEL = WriteConsistencyLevel.DEFAULT;
    String TRANSPORT_ADDRESSES_SEPARATOR_REGEX = ",";
    String IP_PORT_SEPARATOR_REGEX = ":";
}
