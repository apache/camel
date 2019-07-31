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

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Iterator;

import org.apache.camel.Exchange;

/**
 * Strategy for preparing statements when executing SQL queries.
 */
public interface SqlPrepareStatementStrategy {

    /**
     * Prepares the query to be executed.
     *
     * @param query                 the query which may contain named query parameters
     * @param allowNamedParameters  whether named parameters is allowed
     * @param exchange              the current exchange
     * @return the query to actually use, which must be accepted by the JDBC driver.
     */
    String prepareQuery(String query, boolean allowNamedParameters, Exchange exchange) throws SQLException;

    /**
     * Creates the iterator to use when setting query parameters on the prepared statement.
     *
     * @param query            the original query which may contain named parameters
     * @param preparedQuery    the query to actually use, which must be accepted by the JDBC driver.
     * @param expectedParams   number of expected parameters
     * @param exchange         the current exchange
     * @param value            the message body that contains the data for the query parameters
     * @return  the iterator
     * @throws SQLException is thrown if error creating the iterator
     */
    Iterator<?> createPopulateIterator(String query, String preparedQuery, int expectedParams, Exchange exchange, Object value) throws SQLException;

    /**
     * Populates the query parameters on the prepared statement
     *
     * @param ps               the prepared statement
     * @param iterator         the iterator to use for getting the parameter data
     * @param expectedParams   number of expected parameters
     * @throws SQLException is thrown if error populating parameters
     */
    void populateStatement(PreparedStatement ps, Iterator<?> iterator, int expectedParams) throws SQLException;

}
