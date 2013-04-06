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
package org.apache.camel.component.mybatis;

import java.util.Iterator;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @version
 */
public class MyBatisProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(MyBatisProducer.class);
    private String statement;
    private MyBatisEndpoint endpoint;

    public MyBatisProducer(MyBatisEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
        this.statement = endpoint.getStatement();
    }

    public void process(Exchange exchange) throws Exception {
        SqlSession session;

        ExecutorType executorType = endpoint.getExecutorType();
        if (executorType == null) {
            session = endpoint.getSqlSessionFactory().openSession();
        } else {
            session = endpoint.getSqlSessionFactory().openSession(executorType);
        }

        try {
            switch (endpoint.getStatementType()) {
            case SelectOne:
                doSelectOne(exchange, session);
                break;
            case SelectList:
                doSelectList(exchange, session);
                break;
            case Insert:
                doInsert(exchange, session);
                break;
            case InsertList:
                doInsertList(exchange, session);
                break;
            case Update:
                doUpdate(exchange, session);
                break;
            case UpdateList:
                doUpdateList(exchange, session);
                break;
            case Delete:
                doDelete(exchange, session);
                break;
            case DeleteList:
                doDeleteList(exchange, session);
                break;
            default:
                throw new IllegalArgumentException("Unsupported statementType: " + endpoint.getStatementType());
            }
            // flush the batch statements and commit the database connection
            session.commit();
        } catch (Exception e) {
            // discard the pending batch statements and roll the database connection back
            session.rollback();
            throw e;
        } finally {
            // and finally close the session as we're done
            session.close();
        }
    }

    private void doSelectOne(Exchange exchange, SqlSession session) throws Exception {
        Object result;
        Object in = exchange.getIn().getBody();
        if (in != null) {
            LOG.trace("SelectOne: {} using statement: {}", in, statement);
            result = session.selectOne(statement, in);
        } else {
            LOG.trace("SelectOne using statement: {}", statement);
            result = session.selectOne(statement);
        }

        doProcessResult(exchange, result);
    }

    private void doSelectList(Exchange exchange, SqlSession session) throws Exception {
        Object result;
        Object in = exchange.getIn().getBody();
        if (in != null) {
            LOG.trace("SelectList: {} using statement: {}", in, statement);
            result = session.selectList(statement, in);
        } else {
            LOG.trace("SelectList using statement: {}", statement);
            result = session.selectList(statement);
        }

        doProcessResult(exchange, result);
    }

    private void doInsert(Exchange exchange, SqlSession session) throws Exception {
        Object result;
        Object in = exchange.getIn().getBody();
        if (in != null) {
            // lets handle arrays or collections of objects
            Iterator<?> iter = ObjectHelper.createIterator(in);
            while (iter.hasNext()) {
                Object value = iter.next();
                LOG.trace("Inserting: {} using statement: {}", value, statement);
                result = session.insert(statement, value);
                doProcessResult(exchange, result);
            }
        } else {
            LOG.trace("Inserting using statement: {}", statement);
            result = session.insert(statement);
            doProcessResult(exchange, result);
        }
    }

    private void doInsertList(Exchange exchange, SqlSession session) throws Exception {
        Object result;
        Object in = exchange.getIn().getBody();
        if (in != null) {
            // just pass in the body as Object and allow MyBatis to iterate using its own foreach statement
            LOG.trace("Inserting: {} using statement: {}", in, statement);
            result = session.insert(statement, in);
            doProcessResult(exchange, result);
        } else {
            LOG.trace("Inserting using statement: {}", statement);
            result = session.insert(statement);
            doProcessResult(exchange, result);
        }
    }

    private void doUpdate(Exchange exchange, SqlSession session) throws Exception {
        Object result;
        Object in = exchange.getIn().getBody();
        if (in != null) {
            // lets handle arrays or collections of objects
            Iterator<?> iter = ObjectHelper.createIterator(in);
            while (iter.hasNext()) {
                Object value = iter.next();
                LOG.trace("Updating: {} using statement: {}", value, statement);
                result = session.update(statement, value);
                doProcessResult(exchange, result);
            }
        } else {
            LOG.trace("Updating using statement: {}", statement);
            result = session.update(statement);
            doProcessResult(exchange, result);
        }
    }

    private void doUpdateList(Exchange exchange, SqlSession session) throws Exception {
        Object result;
        Object in = exchange.getIn().getBody();
        if (in != null) {
            // just pass in the body as Object and allow MyBatis to iterate using its own foreach statement
            LOG.trace("Updating: {} using statement: {}", in, statement);
            result = session.update(statement, in);
            doProcessResult(exchange, result);
        } else {
            LOG.trace("Updating using statement: {}", statement);
            result = session.update(statement);
            doProcessResult(exchange, result);
        }
    }

    private void doDelete(Exchange exchange, SqlSession session) throws Exception {
        Object result;
        Object in = exchange.getIn().getBody();
        if (in != null) {
            // lets handle arrays or collections of objects
            Iterator<?> iter = ObjectHelper.createIterator(in);
            while (iter.hasNext()) {
                Object value = iter.next();
                LOG.trace("Deleting: {} using statement: {}", value, statement);
                result = session.delete(statement, value);
                doProcessResult(exchange, result);
            }
        } else {
            LOG.trace("Deleting using statement: {}", statement);
            result = session.delete(statement);
            doProcessResult(exchange, result);
        }
    }

    private void doDeleteList(Exchange exchange, SqlSession session) throws Exception {
        Object result;
        Object in = exchange.getIn().getBody();
        if (in != null) {
            // just pass in the body as Object and allow MyBatis to iterate using its own foreach statement
            LOG.trace("Deleting: {} using statement: {}", in, statement);
            result = session.delete(statement, in);
            doProcessResult(exchange, result);
        } else {
            LOG.trace("Deleting using statement: {}", statement);
            result = session.delete(statement);
            doProcessResult(exchange, result);
        }
    }

    private void doProcessResult(Exchange exchange, Object result) {
        if (endpoint.getStatementType() == StatementType.SelectList || endpoint.getStatementType() == StatementType.SelectOne) {
            Message answer = exchange.getIn();
            if (ExchangeHelper.isOutCapable(exchange)) {
                answer = exchange.getOut();
                // preserve headers
                answer.getHeaders().putAll(exchange.getIn().getHeaders());
            }
            // set the result as body for insert
            answer.setBody(result);

            answer.setHeader(MyBatisConstants.MYBATIS_RESULT, result);
            answer.setHeader(MyBatisConstants.MYBATIS_STATEMENT_NAME, statement);
        } else {
            Message msg = exchange.getIn();
            msg.setHeader(MyBatisConstants.MYBATIS_RESULT, result);
            msg.setHeader(MyBatisConstants.MYBATIS_STATEMENT_NAME, statement);
        }
    }

}
