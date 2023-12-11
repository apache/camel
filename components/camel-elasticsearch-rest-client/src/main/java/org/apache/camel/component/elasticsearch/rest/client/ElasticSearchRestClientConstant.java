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
package org.apache.camel.component.elasticsearch.rest.client;

import org.apache.camel.spi.Metadata;

public class ElasticSearchRestClientConstant {

    @Metadata(description = " ID of the object to index or retrieve or delete", javaType = "String")
    public static final String ID = "ID";

    @Metadata(description = "The JSON Query to perform for search", javaType = "String")
    public static final String SEARCH_QUERY = "SEARCH_QUERY";

    @Metadata(description = "Advanced - The JSON Index Settings and/or Mappings Query to perform to create an index",
              javaType = "String")
    public static final String INDEX_SETTINGS = "INDEX_SETTINGS";

    @Metadata(description = "The Index name", javaType = "String")
    public static final String INDEX_NAME = "INDEX_NAME";

    public static final int SOCKET_CONNECTION_TIMEOUT = 30000;
    public static final int SNIFFER_INTERVAL_AND_FAILURE_DELAY = 60000;

}
