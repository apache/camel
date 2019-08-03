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

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.ibatis.session.SqlSession;

public class DefaultMyBatisProcessingStrategy implements MyBatisProcessingStrategy {

    @Override
    public void commit(MyBatisEndpoint endpoint, Exchange exchange, Object data, String consumeStatements) throws Exception {
        SqlSession session = endpoint.getSqlSessionFactory().openSession();
        String[] statements = consumeStatements.split(",");
        try {
            for (String statement : statements) {
                session.update(statement.trim(), data);
            }
            session.commit();
        } catch (Exception e) {
            session.rollback();
            throw e;
        } finally {
            session.close();
        }
    }

    @Override
    public List<?> poll(MyBatisConsumer consumer, MyBatisEndpoint endpoint) throws Exception {
        SqlSession session = endpoint.getSqlSessionFactory().openSession();
        try {
            List<Object> objects = session.selectList(endpoint.getStatement(), null);
            session.commit();
            return objects;
        } catch (Exception e) {
            session.rollback();
            throw e;
        } finally {
            session.close();
        }
    }
}
