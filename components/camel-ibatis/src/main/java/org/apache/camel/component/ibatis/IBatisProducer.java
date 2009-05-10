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
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * @version $Revision$
 */
public class IBatisProducer extends DefaultProducer {
    private static final Log LOG = LogFactory.getLog(IBatisProducer.class);
    private String statement;
    private IBatisEndpoint endpoint;

    public IBatisProducer(IBatisEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
        this.statement = endpoint.getStatement();
    }

    /**
     * Calls insert on the SqlMapClient.
     */
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
        case Default:
            doDefault(exchange); break;
        default:
            doDefault(exchange); break;
        }
    }

    private void doDefault(Exchange exchange) throws Exception {
        Object body = exchange.getIn().getBody();
        if (ObjectHelper.isEmpty(body)) {
            // must be a poll so lets do a query
            doQueryForList(exchange);
        } else {
            // otherwise we insert
            doInsert(exchange);
        }
    }

    private void doQueryForObject(Exchange exchange) throws Exception {
        SqlMapClient client = endpoint.getSqlMapClient();

        Object result;
        Object in = exchange.getIn().getBody();
        if (in != null) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("QueryForObject: " + in + "  using statement: " + statement);
            }
            result = client.queryForObject(statement, in);
        } else {
            if (LOG.isTraceEnabled()) {
                LOG.trace("QueryForObject using statement: " + statement);
            }
            result = client.queryForObject(statement);
        }

        doProcessResult(exchange, result);
    }

    private void doQueryForList(Exchange exchange) throws Exception {
        SqlMapClient client = endpoint.getSqlMapClient();

        Object result;
        Object in = exchange.getIn().getBody();
        if (in != null) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("QueryForList: " + in + "  using statement: " + statement);
            }
            result = client.queryForList(statement, in);
        } else {
            if (LOG.isTraceEnabled()) {
                LOG.trace("QueryForList using statement: " + statement);
            }
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
            Iterator iter = ObjectHelper.createIterator(in);
            while (iter.hasNext()) {
                Object value = iter.next();
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Inserting: " + value + " using statement: " + statement);
                }
                result = client.insert(statement, value);
                doProcessResult(exchange, result);
            }
        } else {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Inserting using statement: " + statement);
            }
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
            Iterator iter = ObjectHelper.createIterator(in);
            while (iter.hasNext()) {
                Object value = iter.next();
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Updating: " + value + " using statement: " + statement);
                }
                result = client.update(statement, value);
                doProcessResult(exchange, result);
            }
        } else {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Updating using statement: " + statement);
            }
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
            Iterator iter = ObjectHelper.createIterator(in);
            while (iter.hasNext()) {
                Object value = iter.next();
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Deleting: " + value + " using statement: " + statement);
                }
                result = client.delete(statement, value);
                doProcessResult(exchange, result);
            }
        } else {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Deleting using statement: " + statement);
            }
            result = client.delete(statement);
            doProcessResult(exchange, result);
        }
    }

    private void doProcessResult(Exchange exchange, Object result) {
        Message msg = exchange.getOut();
        msg.setBody(result);
        msg.setHeader(IBatisConstants.IBATIS_STATEMENT_NAME, statement);
    }
}
