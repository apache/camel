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

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.component.sql.stored.template.ast.InParameter;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.CallableStatementCallback;
import org.springframework.jdbc.core.CallableStatementCreator;

public class CallableStatementWrapper implements StatementWrapper {

    final CallableStatementWrapperFactory factory;

    final String template;

    Map result;

    List<Map<String, ?>> batchItems;

    Integer updateCount;

    BatchCallableStatementCreatorFactory batchFactory;

    public CallableStatementWrapper(String template, CallableStatementWrapperFactory wrapperFactory) {
        this.factory = wrapperFactory;
        this.template = template;
    }

    @Override
    public void call(final WrapperExecuteCallback cb) throws Exception {
        cb.execute(this);
    }


    @Override
    public int[] executeBatch() throws SQLException {

        if (this.batchItems == null) {
            throw new IllegalArgumentException("Batch must have at least one item");
        }

        final Iterator<Map<String, ?>> params = batchItems.iterator();


        return factory.getJdbcTemplate().execute(new CallableStatementCreator() {
            @Override
            public CallableStatement createCallableStatement(Connection connection) throws SQLException {
                return batchFactory.newCallableStatementCreator(params.next()).createCallableStatement(connection);

            }
        }, new CallableStatementCallback<int[]>() {
            @Override
            public int[] doInCallableStatement(CallableStatement callableStatement) throws SQLException, DataAccessException {
                //add first item to batch
                callableStatement.addBatch();

                while (params.hasNext()) {
                    batchFactory.addParameter(callableStatement, params.next());
                    callableStatement.addBatch();
                }
                return callableStatement.executeBatch();
            }
        });
    }


    @Override
    public Integer getUpdateCount() throws SQLException {
        return this.updateCount;
    }


    @Override
    public Object executeStatement() throws SQLException {
        return this.result;
    }

    @Override
    public void populateStatement(Object value, Exchange exchange) throws SQLException {
        this.result = this.factory.getTemplateStoredProcedure(this.template).execute(exchange, value);
        //Spring sets #update-result-1
        this.updateCount = (Integer) this.result.get("#update-count-1");
    }

    @Override
    public void addBatch(Object value, Exchange exchange) {

        if (this.batchFactory == null) {
            this.batchFactory = factory.getTemplateForBatch(template);
        }

        Map<String, Object> batchValues = new HashMap<>();
        //only IN-parameters supported by template
        for (Object param : this.batchFactory.getTemplate().getParameterList()) {
            InParameter inputParameter = (InParameter) param;
            Object paramValue = inputParameter.getValueExtractor().eval(exchange, value);
            batchValues.put(inputParameter.getName(), paramValue);
        }
        if (this.batchItems == null) {
            this.batchItems = new ArrayList<>();
        }
        batchItems.add(batchValues);
    }
}
