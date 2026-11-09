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

package org.apache.camel.test.infra.azure.cosmosdb.common;

public final class AzureCosmosDbProperties {
    /**
     * The endpoint of the Cosmos DB account: registered by the local service, read by the remote one
     */
    public static final String ENDPOINT = "azure.cosmosdb.endpoint";
    /**
     * The key of the Cosmos DB account: registered by the local service, read by the remote one
     */
    public static final String ACCESS_KEY = "azure.cosmosdb.access.key";
    /**
     * The type of the service the tests run against: the local Floci AZ container by default, {@code remote} for the
     * account given by {@link #ENDPOINT} and {@link #ACCESS_KEY}
     */
    public static final String INSTANCE_TYPE = "azure.instance.type";

    private AzureCosmosDbProperties() {

    }
}
