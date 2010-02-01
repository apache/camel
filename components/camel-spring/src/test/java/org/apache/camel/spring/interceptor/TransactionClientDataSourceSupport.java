package org.apache.camel.spring.interceptor;

import javax.sql.DataSource;

import org.apache.camel.spring.SpringTestSupport;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

public class TransactionClientDataSourceSupport extends SpringTestSupport {
    protected JdbcTemplate jdbc;
    protected boolean useTransactionErrorHandler = true;

    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext(
                "/org/apache/camel/spring/interceptor/transactionalClientDataSource.xml");
    }

    @Override
    protected void setUp() throws Exception {
        disableJMX();
        super.setUp();

        // START SNIPPET: e5
        // create database and insert dummy data
        final DataSource ds = getMandatoryBean(DataSource.class, "dataSource");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("create table books (title varchar(50))");
        jdbc.update("insert into books (title) values (?)", new Object[]{"Camel in Action"});
        // END SNIPPET: e5
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        jdbc.execute("drop table books");
        enableJMX();
    }

    public boolean isUseTransactionErrorHandler() {
        return useTransactionErrorHandler;
    }

    @Override
    protected int getExpectedRouteCount() {
        return 0;
    }
}