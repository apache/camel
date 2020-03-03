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
package org.apache.camel.component.mybatis;

import java.util.Iterator;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.ObjectHelper;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MyBatisProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(MyBatisProducer.class);
    private String statement;
    private MyBatisEndpoint endpoint;

    public MyBatisProducer(MyBatisEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
        this.statement = endpoint.getStatement();
    }

    @Override
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
        Object in = getInput(exchange);
        if (in != null) {
            LOG.trace("SelectOne: {} using statement: {}", in, statement);
            result = session.selectOne(statement, in);
        } else {
            LOG.trace("SelectOne using statement: {}", statement);
            result = session.selectOne(statement);
        }

        doProcessResult(exchange, result, session);
    }

    private void doSelectList(Exchange exchange, SqlSession session) throws Exception {
        Object result;
        Object in = getInput(exchange);
        if (in != null) {
            LOG.trace("SelectList: {} using statement: {}", in, statement);
            result = session.selectList(statement, in);
        } else {
            LOG.trace("SelectList using statement: {}", statement);
            result = session.selectList(statement);
        }

        doProcessResult(exchange, result, session);
    }

    private void doInsert(Exchange exchange, SqlSession session) throws Exception {
        Object result;
        Object in = getInput(exchange);
        if (in != null) {
            // lets handle arrays or collections of objects
            Iterator<?> iter = ObjectHelper.createIterator(in);
            while (iter.hasNext()) {
                Object value = iter.next();
                LOG.trace("Inserting: {} using statement: {}", value, statement);
                result = session.insert(statement, value);
                doProcessResult(exchange, result, session);
            }
        } else {
            LOG.trace("Inserting using statement: {}", statement);
            result = session.insert(statement);
            doProcessResult(exchange, result, session);
        }
    }

    private void doInsertList(Exchange exchange, SqlSession session) throws Exception {
        Object result;
        Object in = getInput(exchange);
        if (in != null) {
            // just pass in the body as Object and allow MyBatis to iterate using its own foreach statement
            LOG.trace("Inserting: {} using statement: {}", in, statement);
            result = session.insert(statement, in);
            doProcessResult(exchange, result, session);
        } else {
            LOG.trace("Inserting using statement: {}", statement);
            result = session.insert(statement);
            doProcessResult(exchange, result, session);
        }
    }

    private void doUpdate(Exchange exchange, SqlSession session) throws Exception {
        Object result;
        Object in = getInput(exchange);
        if (in != null) {
            // lets handle arrays or collections of objects
            Iterator<?> iter = ObjectHelper.createIterator(in);
            while (iter.hasNext()) {
                Object value = iter.next();
                LOG.trace("Updating: {} using statement: {}", value, statement);
                result = session.update(statement, value);
                doProcessResult(exchange, result, session);
            }
        } else {
            LOG.trace("Updating using statement: {}", statement);
            result = session.update(statement);
            doProcessResult(exchange, result, session);
        }
    }

    private void doUpdateList(Exchange exchange, SqlSession session) throws Exception {
        Object result;
        Object in = getInput(exchange);
        if (in != null) {
            // just pass in the body as Object and allow MyBatis to iterate using its own foreach statement
            LOG.trace("Updating: {} using statement: {}", in, statement);
            result = session.update(statement, in);
            doProcessResult(exchange, result, session);
        } else {
            LOG.trace("Updating using statement: {}", statement);
            result = session.update(statement);
            doProcessResult(exchange, result, session);
        }
    }

    private void doDelete(Exchange exchange, SqlSession session) throws Exception {
        Object result;
        Object in = getInput(exchange);
        if (in != null) {
            // lets handle arrays or collections of objects
            Iterator<?> iter = ObjectHelper.createIterator(in);
            while (iter.hasNext()) {
                Object value = iter.next();
                LOG.trace("Deleting: {} using statement: {}", value, statement);
                result = session.delete(statement, value);
                doProcessResult(exchange, result, session);
            }
        } else {
            LOG.trace("Deleting using statement: {}", statement);
            result = session.delete(statement);
            doProcessResult(exchange, result, session);
        }
    }

    private void doDeleteList(Exchange exchange, SqlSession session) throws Exception {
        Object result;
        Object in = getInput(exchange);
        if (in != null) {
            // just pass in the body as Object and allow MyBatis to iterate using its own foreach statement
            LOG.trace("Deleting: {} using statement: {}", in, statement);
            result = session.delete(statement, in);
            doProcessResult(exchange, result, session);
        } else {
            LOG.trace("Deleting using statement: {}", statement);
            result = session.delete(statement);
            doProcessResult(exchange, result, session);
        }
    }

    private void doProcessResult(Exchange exchange, Object result, SqlSession session) {
        final String outputHeader = getEndpoint().getOutputHeader();
        Message answer = exchange.getIn();

        if (ExchangeHelper.isOutCapable(exchange)) {
            answer = exchange.getOut();
            // preserve headers
            answer.getHeaders().putAll(exchange.getIn().getHeaders());
            if (outputHeader != null) {
                //if we put the MyBatis result into a header we should preserve the body as well
                answer.setBody(exchange.getIn().getBody());
            }
        }
        if (endpoint.getStatementType() == StatementType.SelectList || endpoint.getStatementType() == StatementType.SelectOne) {
            // we should not set the body if its a stored procedure as the result is already in its OUT parameter
            MappedStatement ms = session.getConfiguration().getMappedStatement(statement);
            if (ms != null && ms.getStatementType() == org.apache.ibatis.mapping.StatementType.CALLABLE) {
                if (result == null) {
                    LOG.trace("Setting result as existing body as MyBatis statement type is Callable, and there was no result.");
                    answer.setBody(exchange.getIn().getBody());
                } else {
                    if (outputHeader != null) {
                        // set the result as header for insert
                        LOG.trace("Setting result as header [{}]: {}", outputHeader, result);
                        answer.setHeader(outputHeader, result);
                    } else {
                        // set the result as body for insert
                        LOG.trace("Setting result as body: {}", result);
                        answer.setBody(result);
                        answer.setHeader(MyBatisConstants.MYBATIS_RESULT, result);
                    }
                }
            } else {
                if (outputHeader != null) {
                    LOG.trace("Setting result as header [{}]: {}", outputHeader, result);
                    answer.setHeader(outputHeader, result);
                } else {
                    // set the result as body for insert
                    LOG.trace("Setting result as body: {}", result);
                    answer.setBody(result);
                    answer.setHeader(MyBatisConstants.MYBATIS_RESULT, result);
                }
            }

        } else {
            final String headerName = (outputHeader != null) ? outputHeader : MyBatisConstants.MYBATIS_RESULT;
            answer.setHeader(headerName, result);
        }
        answer.setHeader(MyBatisConstants.MYBATIS_STATEMENT_NAME, statement);
    }

    @Override
    public MyBatisEndpoint getEndpoint() {
        return (MyBatisEndpoint) super.getEndpoint();
    }

    private Object getInput(final Exchange exchange) {
        final String inputHeader = getEndpoint().getInputHeader();
        if (inputHeader != null) {
            return exchange.getIn().getHeader(inputHeader);
        } else {
            return exchange.getIn().getBody();
        }
    }

}
