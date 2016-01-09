package org.apache.camel.component.sql.unistatement;

import org.apache.camel.Exchange;
import org.apache.camel.component.sql.DefaultSqlEndpoint;
import org.apache.camel.component.sql.SqlConstants;
import org.apache.camel.component.sql.SqlPrepareStatementStrategy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Created by snurmine on 1/8/16.
 */
public class UnistatementFactory {

    final JdbcTemplate jdbcTemplate;

    final SqlPrepareStatementStrategy sqlPrepareStatementStrategy;

    final DefaultSqlEndpoint defaultSqlEndpoint;

    public UnistatementFactory(JdbcTemplate jdbcTemplate, SqlPrepareStatementStrategy sqlPrepareStatementStrategy,
                               DefaultSqlEndpoint defaultSqlEndpoint) {
        this.jdbcTemplate = jdbcTemplate;
        this.sqlPrepareStatementStrategy = sqlPrepareStatementStrategy;
        this.defaultSqlEndpoint = defaultSqlEndpoint;
    }

    public Unistatement create(final Exchange exchange, final String sql, final String preparedQuery, final boolean
            shouldRetrieveGeneratedKeys) {

        PreparedStatementCreator statementCreator = new PreparedStatementCreator() {
            @Override
            public PreparedStatement createPreparedStatement(Connection con) throws SQLException {
                if (!shouldRetrieveGeneratedKeys) {
                    return con.prepareStatement(preparedQuery);
                } else {
                    Object expectedGeneratedColumns = exchange.getIn().getHeader(SqlConstants.SQL_GENERATED_COLUMNS);
                    if (expectedGeneratedColumns == null) {
                        return con.prepareStatement(preparedQuery, Statement.RETURN_GENERATED_KEYS);
                    } else if (expectedGeneratedColumns instanceof String[]) {
                        return con.prepareStatement(preparedQuery, (String[]) expectedGeneratedColumns);
                    } else if (expectedGeneratedColumns instanceof int[]) {
                        return con.prepareStatement(preparedQuery, (int[]) expectedGeneratedColumns);
                    } else {
                        throw new IllegalArgumentException(
                                "Header specifying expected returning columns isn't an instance of String[] or int[] but "
                                        + expectedGeneratedColumns.getClass());
                    }
                }
            }
        };

        return new PreparedStatementUnistatement(statementCreator, jdbcTemplate, sqlPrepareStatementStrategy, defaultSqlEndpoint);
    }




}
