package org.apache.camel.component.sql.unistatement;

import org.apache.camel.component.sql.DefaultSqlEndpoint;
import org.apache.camel.component.sql.SqlPrepareStatementStrategy;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.JdbcUtils;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;

/**
 * Created by snurmine on 1/8/16.
 */
public class PreparedStatementUnistatement implements Unistatement {

    final PreparedStatementCreator preparedStatementCreator;

    final JdbcTemplate jdbcTemplate;

    final SqlPrepareStatementStrategy sqlPrepareStatementStrategy;

    final DefaultSqlEndpoint defaultSqlEndpoint;


    ResultSet rs;

    PreparedStatement preparedStatement;

    public PreparedStatementUnistatement(PreparedStatementCreator preparedStatementCreator, JdbcTemplate
            jdbcTemplate, SqlPrepareStatementStrategy sqlPrepareStatementStrategy, DefaultSqlEndpoint defaultSqlEndpoint) {
        this.preparedStatementCreator = preparedStatementCreator;
        this.jdbcTemplate = jdbcTemplate;
        this.sqlPrepareStatementStrategy = sqlPrepareStatementStrategy;
        this.defaultSqlEndpoint = defaultSqlEndpoint;
    }

    @Override
    public void execute(final UnistatementExecuteCb cb) throws Exception {
        final Unistatement self = this;

            this.jdbcTemplate.execute(preparedStatementCreator, new PreparedStatementCallback<Object>() {
                @Override
                public Object doInPreparedStatement(PreparedStatement preparedStatement) throws SQLException, DataAccessException {
                    try {
                        PreparedStatementUnistatement.this.preparedStatement = preparedStatement;

                        cb.execute(self);
                        return null;
                    }  finally {
                        if (rs != null) {
                            JdbcUtils.closeResultSet(rs);
                        }
                    }
                }
            });

    }

    @Override
    public int getParameterCount() throws SQLException {

        return preparedStatement.getParameterMetaData().getParameterCount();

    }

    @Override
    public void addBatch() throws SQLException {

        preparedStatement.addBatch();

    }

    @Override
    public int[] executeBatch() throws SQLException {
        return preparedStatement.executeBatch();
    }

    @Override
    public boolean executeAndCheckHasValue() throws SQLException {
        return preparedStatement.execute();
    }

    @Override
    public int getUpdateCount() throws SQLException {
        return preparedStatement.getUpdateCount();
    }

    @Override
    public ResultSet getGeneratedKeys() throws SQLException {
        return preparedStatement.getGeneratedKeys();
    }

    @Override
    public void populateStatement(Iterator<?> i, int expected) throws SQLException {
        sqlPrepareStatementStrategy.populateStatement(preparedStatement, i, expected);

    }

    @Override
    public List<?> queryForList(boolean b) throws SQLException {
        rs = preparedStatement.getResultSet();
        List<?> data = defaultSqlEndpoint.queryForList(rs, true);

        return data;
    }

    @Override
    public Object queryForObject() throws SQLException {
        //TODO: check that getResultSet is not called twice?
        rs = preparedStatement.getResultSet();
        return defaultSqlEndpoint.queryForObject(rs);
    }
}
