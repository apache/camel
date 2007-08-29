/**
 * 
 */
package org.apache.camel.component.jdbc;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import javax.sql.DataSource;

/**
 * TODO Provide description for TestDataSource.
 * 
 * @author <a href="mailto:nsandhu@raleys.com">nsandhu</a>
 *
 */
public class TestDataSource implements DataSource{
    private String url, username, password;
    public TestDataSource(String url, String user, String password){
        this.url=url;
        this.username=user;
        this.password=password;
    }
    
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, username, password);
    }

    public Connection getConnection(String username, String password) throws SQLException {
        return DriverManager.getConnection(url, username, password);
    }

    public int getLoginTimeout() throws SQLException {
        return DriverManager.getLoginTimeout();
    }

    public PrintWriter getLogWriter() throws SQLException {
        return DriverManager.getLogWriter();
    }

    public void setLoginTimeout(int seconds) throws SQLException {
       DriverManager.setLoginTimeout(seconds);
    }

    public void setLogWriter(PrintWriter out) throws SQLException {
        DriverManager.setLogWriter(out);
    }

}
