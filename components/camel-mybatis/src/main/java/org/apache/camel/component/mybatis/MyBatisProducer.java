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
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @version $Revision$
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
        switch (endpoint.getStatementType()) {
        case SelectOne:
            doSelectOne(exchange); break;
        case SelectList:
            doSelectList(exchange); break;
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

    private void doSelectOne(Exchange exchange) throws Exception {
        SqlSessionFactory client = endpoint.getSqlSessionFactory();
        SqlSession session = client.openSession();
        try {
            Object result;
            Object in = exchange.getIn().getBody();
            if (in != null) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("SelectOne: " + in + "  using statement: " + statement);
                }
                result = session.selectOne(statement, in);
            } else {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("SelectOne using statement: " + statement);
                }
                result = session.selectOne(statement);
            }

            doProcessResult(exchange, result);
        } finally {
            session.close();
        }
    }

    private void doSelectList(Exchange exchange) throws Exception {
        SqlSessionFactory client = endpoint.getSqlSessionFactory();
        SqlSession session = client.openSession();
        try {
            Object result;
            Object in = exchange.getIn().getBody();
            if (in != null) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("SelectList: " + in + "  using statement: " + statement);
                }
                result = session.selectList(statement, in);
            } else {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("SelectList using statement: " + statement);
                }
                result = session.selectList(statement);
            }

            doProcessResult(exchange, result);
        } finally {
            session.close();
        }
    }

    private void doInsert(Exchange exchange) throws Exception {
        SqlSessionFactory client = endpoint.getSqlSessionFactory();
        SqlSession session = client.openSession();
        try {
            Object result;
            Object in = exchange.getIn().getBody();
            if (in != null) {
                // lets handle arrays or collections of objects
                Iterator iter = ObjectHelper.createIterator(in);
                while (iter.hasNext()) {
                    Object value = iter.next();
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("Inserting: " + value + " using statement: " + statement);
                    }
                    result = session.insert(statement, value);
                    doProcessResult(exchange, result);
                }
            } else {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Inserting using statement: " + statement);
                }
                result = session.insert(statement);
                doProcessResult(exchange, result);
            }
        } finally {
            session.commit();
            session.close();
        }
    }

    private void doUpdate(Exchange exchange) throws Exception {
        SqlSessionFactory client = endpoint.getSqlSessionFactory();
        SqlSession session = client.openSession();
        try {
            Object result;
            Object in = exchange.getIn().getBody();
            if (in != null) {
                // lets handle arrays or collections of objects
                Iterator iter = ObjectHelper.createIterator(in);
                while (iter.hasNext()) {
                    Object value = iter.next();
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("Updating: " + value + " using statement: " + statement);
                    }
                    result = session.update(statement, value);
                    doProcessResult(exchange, result);
                }
            } else {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Updating using statement: " + statement);
                }
                result = session.update(statement);
                doProcessResult(exchange, result);
            }
        } finally {
            session.commit();
            session.close();
        }
    }

    private void doDelete(Exchange exchange) throws Exception {
        SqlSessionFactory client = endpoint.getSqlSessionFactory();
        SqlSession session = client.openSession();
        try {
            Object result;
            Object in = exchange.getIn().getBody();
            if (in != null) {
                // lets handle arrays or collections of objects
                Iterator iter = ObjectHelper.createIterator(in);
                while (iter.hasNext()) {
                    Object value = iter.next();
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("Deleting: " + value + " using statement: " + statement);
                    }
                    result = session.delete(statement, value);
                    doProcessResult(exchange, result);
                }
            } else {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Deleting using statement: " + statement);
                }
                result = session.delete(statement);
                doProcessResult(exchange, result);
            }
        } finally {
            session.commit();
            session.close();
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
