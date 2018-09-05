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
package org.apache.camel.component.elsql;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Iterator;

import com.opengamma.elsql.SpringSqlParams;
import org.apache.camel.Exchange;
import org.apache.camel.component.sql.SqlPrepareStatementStrategy;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

public class ElsqlSqlPrepareStatementStrategy implements SqlPrepareStatementStrategy {

    public String prepareQuery(String query, boolean allowNamedParameters, Exchange exchange) throws SQLException {
        return query;
    }

    public Iterator<?> createPopulateIterator(String query, String preparedQuery, int expectedParams, Exchange exchange, Object value) throws SQLException {
        return null;
    }

    public void populateStatement(PreparedStatement ps, Iterator<?> iterator, int expectedParams) throws SQLException {
        // noop
    }
}
