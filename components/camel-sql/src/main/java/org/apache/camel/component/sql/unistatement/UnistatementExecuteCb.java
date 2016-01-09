package org.apache.camel.component.sql.unistatement;

import org.springframework.dao.DataAccessException;

import java.sql.SQLException;

/**
 * Created by snurmine on 1/8/16.
 */
public interface UnistatementExecuteCb {

    void execute(Unistatement unistatement) throws SQLException, DataAccessException;
}
