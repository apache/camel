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
package org.apache.camel.component.sql.stored;

import java.sql.SQLException;
import java.util.Iterator;

import org.apache.camel.Exchange;
import org.apache.camel.component.sql.SqlHelper;
import org.apache.camel.support.DefaultProducer;
import org.springframework.dao.DataAccessException;

public class SqlStoredProducer extends DefaultProducer {

    private String resolvedTemplate;
    private CallableStatementWrapperFactory callableStatementWrapperFactory;

    public SqlStoredProducer(SqlStoredEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public SqlStoredEndpoint getEndpoint() {
        return (SqlStoredEndpoint) super.getEndpoint();
    }

    @Override
    public void process(final Exchange exchange) throws Exception {
        StatementWrapper statementWrapper = createStatement(exchange);
        statementWrapper.call(new WrapperExecuteCallback() {
            @Override
            public void execute(StatementWrapper ps) throws SQLException, DataAccessException {
                // transfer incoming message body data to prepared statement parameters, if necessary
                if (getEndpoint().isBatch()) {
                    Iterator<?> iterator;
                    if (getEndpoint().isUseMessageBodyForTemplate()) {
                        iterator = exchange.getIn().getHeader(SqlStoredConstants.SQL_STORED_PARAMETERS, Iterator.class);
                    } else {
                        iterator = exchange.getIn().getBody(Iterator.class);
                    }

                    if (iterator == null) {
                        throw new IllegalStateException("batch=true but Iterator cannot be found from body or header");
                    }
                    while (iterator.hasNext()) {
                        Object value = iterator.next();
                        ps.addBatch(value, exchange);
                    }
                } else {
                    Object value;
                    if (getEndpoint().isUseMessageBodyForTemplate()) {
                        value = exchange.getIn().getHeader(SqlStoredConstants.SQL_STORED_PARAMETERS);
                    } else {
                        value = exchange.getIn().getBody();
                    }
                    ps.populateStatement(value, exchange);
                }

                // call the prepared statement and populate the outgoing message
                if (getEndpoint().isBatch()) {
                    int[] updateCounts = ps.executeBatch();
                    int total = 0;
                    for (int count : updateCounts) {
                        total += count;
                    }
                    exchange.getIn().setHeader(SqlStoredConstants.SQL_STORED_UPDATE_COUNT, total);
                } else {
                    Object result = ps.executeStatement();
                    // preserve headers first, so we can override the SQL_ROW_COUNT and SQL_UPDATE_COUNT headers
                    // if statement returns them
                    exchange.getOut().getHeaders().putAll(exchange.getIn().getHeaders());

                    if (result != null) {
                        if (getEndpoint().isNoop()) {
                            exchange.getOut().setBody(exchange.getIn().getBody());
                        } else if (getEndpoint().getOutputHeader() != null) {
                            exchange.getOut().setBody(exchange.getIn().getBody());
                            exchange.getOut().setHeader(getEndpoint().getOutputHeader(), result);
                        } else {
                            exchange.getOut().setBody(result);
                        }
                    }
                    // for noop=true we still want to enrich with the headers

                    if (ps.getUpdateCount() != null) {
                        exchange.getOut().setHeader(SqlStoredConstants.SQL_STORED_UPDATE_COUNT, ps.getUpdateCount());
                    }
                }
            }
        });
    }

    private StatementWrapper createStatement(Exchange exchange) throws SQLException {
        String sql;
        if (getEndpoint().isUseMessageBodyForTemplate()) {
            sql = exchange.getIn().getBody(String.class);
        } else {
            String templateHeader = exchange.getIn().getHeader(SqlStoredConstants.SQL_STORED_TEMPLATE, String.class);
            sql = templateHeader != null ? templateHeader : resolvedTemplate;
        }

        try {
            sql = SqlHelper.resolveQuery(getEndpoint().getCamelContext(), sql, null);
        } catch (Exception e) {
            throw new SQLException("Error loading template resource: " + sql, e);
        }

        return getEndpoint().getWrapperFactory().create(sql);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        resolvedTemplate = SqlHelper.resolveQuery(getEndpoint().getCamelContext(), getEndpoint().getTemplate(), null);
    }
}
