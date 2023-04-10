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
package org.apache.camel.processor.aggregate.jdbc;

import javax.sql.DataSource;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * PostgreSQL specific {@link JdbcAggregationRepository} that deals with SQL Violation Exceptions using special
 * {@code INSERT INTO .. ON CONFLICT DO NOTHING} claues.
 */
public class ClusteredPostgresAggregationRepository extends ClusteredJdbcAggregationRepository {

    /**
     * Creates an aggregation repository
     */
    public ClusteredPostgresAggregationRepository() {
    }

    /**
     * Creates an aggregation repository with the three mandatory parameters
     */
    public ClusteredPostgresAggregationRepository(PlatformTransactionManager transactionManager, String repositoryName,
                                                  DataSource dataSource) {
        super(transactionManager, repositoryName, dataSource);
    }

    /**
     * Inserts a new record into the given repository table
     *
     * @param camelContext   the current CamelContext
     * @param correlationId  the correlation key
     * @param exchange       the aggregated exchange
     * @param repositoryName The name of the table
     */
    @Override
    protected void insert(
            final CamelContext camelContext, final String correlationId, final Exchange exchange, String repositoryName,
            final Long version, final boolean completed)
            throws Exception {
        // The default totalParameterIndex is 2 for ID and Exchange. Depending on logic this will be increased
        int totalParameterIndex = 2;
        StringBuilder queryBuilder = new StringBuilder()
                .append("INSERT INTO ").append(repositoryName)
                .append('(')
                .append(EXCHANGE).append(", ")
                .append(ID);

        if (isStoreBodyAsText()) {
            queryBuilder.append(", ").append(BODY);
            totalParameterIndex++;
        }

        if (hasHeadersToStoreAsText()) {
            for (String headerName : getHeadersToStoreAsText()) {
                queryBuilder.append(", ").append(headerName);
                totalParameterIndex++;
            }
        }

        queryBuilder.append(") VALUES (");

        queryBuilder.append("?, ".repeat(Math.max(0, totalParameterIndex - 1)));
        queryBuilder.append("?)");

        queryBuilder.append(" ON CONFLICT DO NOTHING");

        String sql = queryBuilder.toString();

        int updateCount = insertHelper(camelContext, correlationId, exchange, sql, 1L, completed);
        if (updateCount == 0 && getRepositoryName().equals(repositoryName)) {
            throw new DataIntegrityViolationException("No row was inserted due to data violation");
        }
    }

}
