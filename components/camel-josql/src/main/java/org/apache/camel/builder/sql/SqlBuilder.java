/**
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
package org.apache.camel.builder.sql;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Message;
import org.apache.camel.Predicate;
import org.apache.camel.RuntimeExpressionException;
import org.apache.camel.util.ObjectHelper;

import org.josql.Query;
import org.josql.QueryExecutionException;
import org.josql.QueryParseException;

/**
 * A builder of SQL {@link org.apache.camel.Expression} and
 * {@link org.apache.camel.Predicate} implementations
 * 
 * @version 
 */
public class SqlBuilder implements Expression, Predicate {

    private Query query;
    private Map<String, Object> variables = new HashMap<String, Object>();

    public SqlBuilder(Query query) {
        this.query = query;
    }

    public <T> T evaluate(Exchange exchange, Class<T> type) {
        Object result = evaluateQuery(exchange);
        return exchange.getContext().getTypeConverter().convertTo(type, result);
    }

    public boolean matches(Exchange exchange) {
        List<?> list = evaluateQuery(exchange);
        return matches(exchange, list);
    }

    public void assertMatches(String text, Exchange exchange) throws AssertionError {
        List<?> list = evaluateQuery(exchange);
        if (!matches(exchange, list)) {
            throw new AssertionError(this + " failed on " + exchange + " as found " + list);
        }
    }

    // Builder API
    // -----------------------------------------------------------------------

    /**
     * Creates a new builder for the given SQL query string
     * 
     * @param sql the SQL query to perform
     * @return a new builder
     * @throws QueryParseException if there is an issue with the SQL
     */
    public static SqlBuilder sql(String sql) throws QueryParseException {
        Query q = new Query();
        q.parse(sql);
        return new SqlBuilder(q);
    }

    /**
     * Adds the variable value to be used by the SQL query
     */
    public SqlBuilder variable(String name, Object value) {
        getVariables().put(name, value);
        return this;
    }

    // Properties
    // -----------------------------------------------------------------------
    public Map<String, Object> getVariables() {
        return variables;
    }

    public void setVariables(Map<String, Object> properties) {
        this.variables = properties;
    }

    // Implementation methods
    // -----------------------------------------------------------------------
    protected boolean matches(Exchange exchange, List<?> list) {
        return ObjectHelper.matches(list);
    }

    protected List<?> evaluateQuery(Exchange exchange) {
        configureQuery(exchange);
        Message in = exchange.getIn();
        List<?> list = in.getBody(List.class);
        if (list == null) {
            list = Collections.singletonList(in.getBody());
        }
        try {
            return query.execute(list).getResults();
        } catch (QueryExecutionException e) {
            throw new RuntimeExpressionException(e);
        }
    }

    protected void configureQuery(Exchange exchange) {
        // lets pass in the headers as variables that the SQL can use
        addVariables(exchange.getProperties());
        addVariables(exchange.getIn().getHeaders());
        addVariables(getVariables());

        query.setVariable("exchange", exchange);
        query.setVariable("in", exchange.getIn());
        // To avoid the side effect of creating out message without notice
        if (exchange.hasOut()) {
            query.setVariable("out", exchange.getOut());
        }
    }

    protected void addVariables(Map<String, Object> map) {
        Set<Map.Entry<String, Object>> propertyEntries = map.entrySet();
        for (Map.Entry<String, Object> entry : propertyEntries) {
            query.setVariable(entry.getKey(), entry.getValue());
        }
    }
}
