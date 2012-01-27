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

import com.ibatis.sqlmap.client.SqlMapClient;
import org.apache.camel.Exchange;
import org.apache.camel.component.ibatis.IBatisConsumer;
import org.apache.camel.component.ibatis.IBatisEndpoint;

/**
 * Default strategy for consuming messages for a route
 */
public class DefaultIBatisProcessingStategy implements IBatisProcessingStrategy {

    private int isolation = Connection.TRANSACTION_REPEATABLE_READ;

    public void commit(IBatisEndpoint endpoint, Exchange exchange, Object data, String consumeStatements) throws Exception {
        SqlMapClient client = endpoint.getSqlMapClient();
        boolean useTrans = endpoint.isUseTransactions();
        String[] statements = consumeStatements.split(",");
        try {
            if (useTrans) {
                client.startTransaction(isolation);
            }
            for (String statement : statements) {
                client.update(statement.trim(), data);
            }
            if (useTrans) {
                client.commitTransaction();
            }
        } finally {
            if (useTrans) {
                client.endTransaction();
            }
        }
    }

    @SuppressWarnings("unchecked")
    public List<Object> poll(IBatisConsumer consumer, IBatisEndpoint endpoint) throws Exception {
        SqlMapClient client = endpoint.getSqlMapClient();
        return client.queryForList(endpoint.getStatement(), null);
    }

    public int getIsolation() {
        return isolation;
    }

    public void setIsolation(int isolation) {
        this.isolation = isolation;
    }
}
