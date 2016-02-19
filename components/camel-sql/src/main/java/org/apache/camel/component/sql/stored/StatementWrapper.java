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
package org.apache.camel.component.sql.stored;

import java.sql.SQLException;

import org.apache.camel.Exchange;

/**
 * Wrapper that simplifies operations on  {@link java.sql.CallableStatement} in {@link SqlStoredProducer}.
 * Wrappers are stateful objects and must not be reused.
 */
public interface StatementWrapper {

    void call(WrapperExecuteCallback cb) throws Exception;

    int[] executeBatch() throws SQLException;

    Integer getUpdateCount() throws SQLException;

    Object executeStatement() throws SQLException;

    void populateStatement(Object value, Exchange exchange) throws SQLException;

    void addBatch(Object value, Exchange exchange);
}
