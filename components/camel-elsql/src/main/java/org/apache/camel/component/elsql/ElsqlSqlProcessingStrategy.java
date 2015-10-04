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

import com.opengamma.elsql.ElSql;
import org.apache.camel.Exchange;
import org.apache.camel.component.sql.SqlEndpoint;
import org.apache.camel.component.sql.SqlProcessingStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;

public class ElsqlSqlProcessingStrategy implements SqlProcessingStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(ElsqlSqlProcessingStrategy.class);
    private final String elSqlName;
    private final ElSql elSql;

    public ElsqlSqlProcessingStrategy(String elSqlName, ElSql elSql) {
        this.elSqlName = elSqlName;
        this.elSql = elSql;
    }

    @Override
    public int commit(final SqlEndpoint endpoint, final Exchange exchange, final Object data, final JdbcTemplate jdbcTemplate, final String query) throws Exception {
        String sql = elSql.getSql(elSqlName, new ElsqlSqlParams(exchange, data));

        return jdbcTemplate.execute(sql, new PreparedStatementCallback<Integer>() {
            @Override
            public Integer doInPreparedStatement(PreparedStatement ps) throws SQLException, DataAccessException {
                ps.execute();

                int updateCount = ps.getUpdateCount();
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Update count {}", updateCount);
                }
                return updateCount;
            }
        });
    }

    @Override
    public int commitBatchComplete(final SqlEndpoint endpoint, final JdbcTemplate jdbcTemplate, final String query) throws Exception {
        return 0;
    }
}
