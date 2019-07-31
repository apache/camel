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

/**
 * Couchbase Constants and default connection parameters
 */

public interface CouchbaseConstants {

    String COUCHBASE_URI_ERROR = "Invalid URI. Format must be of the form couchbase:http[s]://hostname[:port]/bucket?[options...]";
    String COUCHBASE_PUT = "CCB_PUT";
    String COUCHBASE_GET = "CCB_GET";
    String COUCHBASE_DELETE = "CCB_DEL";
    String DEFAULT_DESIGN_DOCUMENT_NAME = "beer";
    String DEFAULT_VIEWNAME = "brewery_beers";
    String HEADER_KEY = "CCB_KEY";
    String HEADER_ID = "CCB_ID";
    String HEADER_TTL = "CCB_TTL";
    String HEADER_DESIGN_DOCUMENT_NAME = "CCB_DDN";
    String HEADER_VIEWNAME = "CCB_VN";

    int DEFAULT_PRODUCER_RETRIES = 2;
    int DEFAULT_PAUSE_BETWEEN_RETRIES = 5000;
    int DEFAULT_COUCHBASE_PORT = 8091;
    int DEFAULT_TTL = 0;
    long DEFAULT_OP_TIMEOUT = 2500;
    int DEFAULT_TIMEOUT_EXCEPTION_THRESHOLD = 998;
    int DEFAULT_READ_BUFFER_SIZE = 16384;
    long DEFAULT_OP_QUEUE_MAX_BLOCK_TIME = 10000;
    long DEFAULT_MAX_RECONNECT_DELAY = 30000;
    long DEFAULT_OBS_POLL_INTERVAL = 400;
    long DEFAULT_OBS_TIMEOUT = -1;
    String DEFAULT_CONSUME_PROCESSED_STRATEGY = "none";

}
