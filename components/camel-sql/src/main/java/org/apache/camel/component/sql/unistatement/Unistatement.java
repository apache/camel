package org.apache.camel.component.sql.unistatement;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;

/**
 * Created by snurmine on 1/8/16.
 */
public interface Unistatement {

    void execute(UnistatementExecuteCb cb) throws Exception;


    int getParameterCount() throws SQLException;

    void addBatch() throws SQLException;

    int[] executeBatch() throws SQLException;

    boolean executeAndCheckHasValue() throws SQLException;

    int getUpdateCount() throws SQLException;

    ResultSet getGeneratedKeys() throws SQLException;

    void populateStatement(Iterator<?> i, int expected) throws SQLException;

    List<?> queryForList(boolean b) throws SQLException;

    Object queryForObject() throws SQLException;

}
