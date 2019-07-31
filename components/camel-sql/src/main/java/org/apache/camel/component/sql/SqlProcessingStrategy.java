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
package org.apache.camel.component.sql;

import org.apache.camel.Exchange;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Processing strategy for dealing with SQL when consuming.
 */
public interface SqlProcessingStrategy {

    /**
     * Commit callback if there are a query to be run after processing.
     *
     * @param endpoint     the endpoint
     * @param exchange     The exchange after it has been processed
     * @param data         The original data delivered to the route
     * @param jdbcTemplate The JDBC template
     * @param query        The SQL query to execute
     * @return the update count if the query returned an update count
     * @throws Exception can be thrown in case of error
     */
    int commit(DefaultSqlEndpoint endpoint, Exchange exchange, Object data, JdbcTemplate jdbcTemplate, String query) throws Exception;

    /**
     * Commit callback when the batch is complete. This allows you to do one extra query after all rows has been processed in the batch.
     *
     * @param endpoint     the endpoint
     * @param jdbcTemplate The JDBC template
     * @param query        The SQL query to execute
     * @return the update count if the query returned an update count
     * @throws Exception can be thrown in case of error
     */
    int commitBatchComplete(DefaultSqlEndpoint endpoint, JdbcTemplate jdbcTemplate, String query) throws Exception;

}
