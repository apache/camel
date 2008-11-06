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
package org.apache.camel.component.ibatis.strategy;

import java.sql.Connection;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.component.ibatis.IBatisEndpoint;
import org.apache.camel.component.ibatis.IBatisPollingConsumer;

import com.ibatis.sqlmap.client.SqlMapClient;

/**
 * Default strategy for consuming messages for a route
 */
public class DefaultIBatisProcessingStategy implements IBatisProcessingStrategy {
    /**
     * Calls update on the SqlMapClient using the consumeStatement.
     * Will call multiple statements if the consumeStatement is a comma separated list.
     * The parameter passed to the statement is the original data delivered to the route.
     */
    public void commit(IBatisEndpoint endpoint, Exchange exchange, Object data, String consumeStatement) throws Exception {

        SqlMapClient client = endpoint.getSqlMapClient();
        boolean useTrans = endpoint.isUseTransactions();
        String[] statements = consumeStatement.split(",");
        try{
            if (useTrans){
                client.startTransaction(Connection.TRANSACTION_REPEATABLE_READ);
            }
            for (String statement: statements) {
                client.update(statement.trim(), data);
            }
            if (useTrans){
                client.commitTransaction();
            }
        } finally {
            if (useTrans) {
                client.endTransaction();
            }
        }
    }

    public List poll(IBatisPollingConsumer consumer, IBatisEndpoint endpoint) throws Exception {
        SqlMapClient client = endpoint.getSqlMapClient();
        return client.queryForList(endpoint.getStatement(), null);
    }
}
