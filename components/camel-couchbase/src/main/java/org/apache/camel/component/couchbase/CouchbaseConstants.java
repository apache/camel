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
package org.apache.camel.component.couchbase;

import org.apache.camel.spi.Metadata;

/**
 * Couchbase Constants and default connection parameters
 */

public interface CouchbaseConstants {

    String COUCHBASE_URI_ERROR
            = "Invalid URI. Format must be of the form couchbase:http[s]://hostname[:port]?bucket=[bucket][&options...]";
    String COUCHBASE_PUT = "CCB_PUT";
    String COUCHBASE_GET = "CCB_GET";
    String COUCHBASE_DELETE = "CCB_DEL";
    String DEFAULT_DESIGN_DOCUMENT_NAME = "beer";
    String DEFAULT_VIEWNAME = "brewery_beers";
    @Metadata(label = "consumer", description = "The key", javaType = "String")
    String HEADER_KEY = "CCB_KEY";
    @Metadata(description = "The document id", javaType = "String")
    String HEADER_ID = "CCB_ID";
    @Metadata(label = "producer", description = "The expiry for the document in seconds", javaType = "String")
    String HEADER_TTL = "CCB_TTL";
    @Metadata(label = "consumer", description = "The design document name", javaType = "String")
    String HEADER_DESIGN_DOCUMENT_NAME = "CCB_DDN";
    @Metadata(label = "consumer", description = "The view name", javaType = "String")
    String HEADER_VIEWNAME = "CCB_VN";

    @Metadata(label = "consumer", description = "The resume action to execute when resuming.", javaType = "String")
    String COUCHBASE_RESUME_ACTION = "CamelCqlResumeQuery";

    int DEFAULT_PRODUCER_RETRIES = 2;
    int DEFAULT_PAUSE_BETWEEN_RETRIES = 5000;
    int DEFAULT_COUCHBASE_PORT = 8091;
    int DEFAULT_TTL = 0;
    long DEFAULT_QUERY_TIMEOUT = 2500;
    long DEFAULT_CONNECT_TIMEOUT = 30000;

    String DEFAULT_CONSUME_PROCESSED_STRATEGY = "none";

}
