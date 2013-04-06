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
package org.apache.camel.component.ibatis;

import java.util.Iterator;

import com.ibatis.sqlmap.client.SqlMapClient;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @version 
 */
public class IBatisProducer extends DefaultProducer {
    private static final Logger LOG = LoggerFactory.getLogger(IBatisProducer.class);
    private String statement;
    private IBatisEndpoint endpoint;

    public IBatisProducer(IBatisEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
        this.statement = endpoint.getStatement();
    }

    public void process(Exchange exchange) throws Exception {
        switch (endpoint.getStatementType()) {
        case QueryForObject:
            doQueryForObject(exchange); break;
        case QueryForList:
            doQueryForList(exchange); break;
        case Insert:
            doInsert(exchange); break;
        case Update:
            doUpdate(exchange); break;
        case Delete:
            doDelete(exchange); break;
        default:
            throw new IllegalArgumentException("Unsupported statementType: " + endpoint.getStatementType());
        }
    }

    private void doQueryForObject(Exchange exchange) throws Exception {
        SqlMapClient client = endpoint.getSqlMapClient();

        Object result;
        Object in = exchange.getIn().getBody();
        if (in != null) {
            LOG.trace("QueryForObject: {} using statement: {}", in, statement);
            result = client.queryForObject(statement, in);
        } else {
            LOG.trace("QueryForObject using statement: {}", statement);
            result = client.queryForObject(statement);
        }

        doProcessResult(exchange, result);
    }

    private void doQueryForList(Exchange exchange) throws Exception {
        SqlMapClient client = endpoint.getSqlMapClient();

        Object result;
        Object in = exchange.getIn().getBody();
        if (in != null) {
            LOG.trace("QueryForList: {} using statement: {}", in, statement);
            result = client.queryForList(statement, in);
        } else {
            LOG.trace("QueryForList using statement: {}", statement);
            result = client.queryForList(statement);
        }

        doProcessResult(exchange, result);
    }

    private void doInsert(Exchange exchange) throws Exception {
        SqlMapClient client = endpoint.getSqlMapClient();

        Object result;
        Object in = exchange.getIn().getBody();
        if (in != null) {
            // lets handle arrays or collections of objects
            Iterator<?> iter = ObjectHelper.createIterator(in);
            while (iter.hasNext()) {
                Object value = iter.next();
                LOG.trace("Inserting: {} using statement: {}", value, statement);
                result = client.insert(statement, value);
                doProcessResult(exchange, result);
            }
        } else {
            LOG.trace("Inserting using statement: {}", statement);
            result = client.insert(statement);
            doProcessResult(exchange, result);
        }
    }

    private void doUpdate(Exchange exchange) throws Exception {
        SqlMapClient client = endpoint.getSqlMapClient();

        Object result;
        Object in = exchange.getIn().getBody();
        if (in != null) {
            // lets handle arrays or collections of objects
            Iterator<?> iter = ObjectHelper.createIterator(in);
            while (iter.hasNext()) {
                Object value = iter.next();
                LOG.trace("Updating: {} using statement: {}", value, statement);
                result = client.update(statement, value);
                doProcessResult(exchange, result);
            }
        } else {
            LOG.trace("Updating using statement: {}", statement);
            result = client.update(statement);
            doProcessResult(exchange, result);
        }
    }

    private void doDelete(Exchange exchange) throws Exception {
        SqlMapClient client = endpoint.getSqlMapClient();

        Object result;
        Object in = exchange.getIn().getBody();
        if (in != null) {
            // lets handle arrays or collections of objects
            Iterator<?> iter = ObjectHelper.createIterator(in);
            while (iter.hasNext()) {
                Object value = iter.next();
                LOG.trace("Deleting: {} using statement: {}", value, statement);
                result = client.delete(statement, value);
                doProcessResult(exchange, result);
            }
        } else {
            LOG.trace("Deleting using statement: {}", statement);
            result = client.delete(statement);
            doProcessResult(exchange, result);
        }
    }

    private void doProcessResult(Exchange exchange, Object result) {
        if (endpoint.getStatementType() == StatementType.QueryForList || endpoint.getStatementType() == StatementType.QueryForObject) {
            Message answer = exchange.getIn();
            if (ExchangeHelper.isOutCapable(exchange)) {
                answer = exchange.getOut();
                // preserve headers
                answer.getHeaders().putAll(exchange.getIn().getHeaders());
            }
            // set the result as body for insert
            answer.setBody(result);

            answer.setHeader(IBatisConstants.IBATIS_RESULT, result);
            answer.setHeader(IBatisConstants.IBATIS_STATEMENT_NAME, statement);
        } else {
            Message msg = exchange.getIn();
            msg.setHeader(IBatisConstants.IBATIS_RESULT, result);
            msg.setHeader(IBatisConstants.IBATIS_STATEMENT_NAME, statement);
        }
    }
}
