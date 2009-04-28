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
import java.util.List;

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
        SqlMapClient client = endpoint.getSqlMapClient();
        Object body = exchange.getIn().getBody();
        if (ObjectHelper.isEmpty(body)) {
            // must be a poll so lets do a query
            Message msg = exchange.getOut();
            if (LOG.isTraceEnabled()) {
                LOG.trace("Querying for list: " + statement);
            }
            List list = client.queryForList(statement);
            msg.setBody(list);
            msg.setHeader(IBatisConstants.IBATIS_STATEMENT_NAME, statement);
        } else {
            // lets handle arrays or collections of objects
            Iterator iter = ObjectHelper.createIterator(body);
            while (iter.hasNext()) {
                Object value = iter.next();
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Inserting: " + value + " using statement: " + statement);
                }
                client.insert(statement, value);
            }
        }
    }
}
