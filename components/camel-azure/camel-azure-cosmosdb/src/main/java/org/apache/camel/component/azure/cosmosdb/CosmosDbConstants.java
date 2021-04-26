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
    // common headers, set by consumer and evaluated by producer
    public static final String DATABASE_NAME = HEADER_PREFIX + "DatabaseName";
    public static final String THROUGHPUT_PROPERTIES = HEADER_PREFIX + "ThroughputProperties";
    public static final String DATABASE_REQUEST_OPTIONS = HEADER_PREFIX + "DatabaseRequestOptions";
    public static final String CREATE_DATABASE_IF_NOT_EXIST = HEADER_PREFIX + "createDatabaseIfNotExist";
    public static final String CREATE_CONTAINER_IF_NOT_EXIST = HEADER_PREFIX + "CreateContainerIfNotExist";

    // headers set by the consumer only

    // headers evaluated by the producer only
    public static final String OPERATION = HEADER_PREFIX + "Operation";

    private CosmosDbConstants() {
    }
}
