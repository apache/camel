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

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.ObjectHelper;

import com.ibatis.sqlmap.client.SqlMapClient;

/**
 * @version $Revision$
 */
public class IBatisProducer extends DefaultProducer<Exchange> {
    private String statement;
    private IBatisEndpoint endpoint;

    public IBatisProducer(IBatisEndpoint endpoint) {
        super(endpoint);
        statement = endpoint.getStatement();
        this.endpoint = endpoint;
    }

    @Override
    public Endpoint getEndpoint() {
        return (IBatisEndpoint) super.getEndpoint();
    }

    /**
     * Calls insert on the SqlMapClient.
     */
    public void process(Exchange exchange) throws Exception {
        SqlMapClient client = endpoint.getSqlMapClient();
        Object body = exchange.getIn().getBody();
        if (body == null) {
            // must be a poll so lets do a query
            Message msg = exchange.getOut(true);
            List list = client.queryForList(statement);
            msg.setBody(list);
            msg.setHeader("org.apache.camel.ibatis.queryName", statement);
        } else {
            // lets handle arrays or collections of objects
            Iterator iter = ObjectHelper.createIterator(body);
            while (iter.hasNext()) {
                client.insert(statement, iter.next());
            }
        }
    }
}
