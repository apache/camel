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
package org.apache.camel.component.google.bigquery.sql;

/**
 * Output type for SELECT query results.
 */
public enum OutputType {
    /**
     * Returns all results as {@code List<Map<String, Object>>} (default). Loads all rows into memory. Sets pagination
     * headers for manual page control.
     * <p>
     * Use when: result set fits in memory, need multiple access to rows, require manual pagination.
     */
    SELECT_LIST,

    /**
     * Returns a streaming {@code Iterator<Map<String, Object>>}. Memory-efficient lazy loading for large result sets.
     * Uses pageSize for internal fetch size. Pagination handled automatically. Does NOT set NextPageToken or JobId
     * headers.
     * <p>
     * Use when: processing large result sets, one-time row processing, streaming to another endpoint.
     */
    STREAM_LIST
}
