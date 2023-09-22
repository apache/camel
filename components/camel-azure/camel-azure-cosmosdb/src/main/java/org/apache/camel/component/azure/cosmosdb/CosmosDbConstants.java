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
package org.apache.camel.component.azure.cosmosdb;

public final class CosmosDbConstants {
    private static final String HEADER_PREFIX = "CamelAzureCosmosDb";

    // headers evaluated by the producer only
    public static final String DATABASE_NAME = HEADER_PREFIX + "DatabaseName";
    public static final String CONTAINER_NAME = HEADER_PREFIX + "ContainerName";
    public static final String OPERATION = HEADER_PREFIX + "Operation";
    public static final String QUERY = HEADER_PREFIX + "Query";
    public static final String QUERY_REQUEST_OPTIONS = HEADER_PREFIX + "QueryRequestOptions";
    public static final String CREATE_DATABASE_IF_NOT_EXIST = HEADER_PREFIX + "CreateDatabaseIfNotExist";
    public static final String CREATE_CONTAINER_IF_NOT_EXIST = HEADER_PREFIX + "CreateContainerIfNotExist";
    public static final String INDEXING_POLICY = HEADER_PREFIX + "IndexingPolicy";
    public static final String THROUGHPUT_PROPERTIES = HEADER_PREFIX + "ThroughputProperties";
    public static final String DATABASE_REQUEST_OPTIONS = HEADER_PREFIX + "DatabaseRequestOptions";
    public static final String CONTAINER_PARTITION_KEY_PATH = HEADER_PREFIX + "ContainerPartitionKeyPath";
    public static final String CONTAINER_REQUEST_OPTIONS = HEADER_PREFIX + "ContainerRequestOptions";
    public static final String ITEM_PARTITION_KEY = HEADER_PREFIX + "ItemPartitionKey";
    public static final String ITEM_REQUEST_OPTIONS = HEADER_PREFIX + "ItemRequestOptions";
    public static final String ITEM_ID = HEADER_PREFIX + "ItemId";

    // headers set by the producer
    public static final String RESOURCE_ID = HEADER_PREFIX + "RecourseId";
    public static final String E_TAG = HEADER_PREFIX + "Etag";
    public static final String TIMESTAMP = HEADER_PREFIX + "Timestamp";
    public static final String RESPONSE_HEADERS = HEADER_PREFIX + "ResponseHeaders";
    public static final String STATUS_CODE = HEADER_PREFIX + "StatusCode";
    public static final String DEFAULT_TIME_TO_LIVE_SECONDS = HEADER_PREFIX + "DefaultTimeToLiveInSeconds";
    public static final String MANUAL_THROUGHPUT = HEADER_PREFIX + "ManualThroughput";
    public static final String AUTOSCALE_MAX_THROUGHPUT = HEADER_PREFIX + "AutoscaleMaxThroughput";

    private CosmosDbConstants() {
    }
}
