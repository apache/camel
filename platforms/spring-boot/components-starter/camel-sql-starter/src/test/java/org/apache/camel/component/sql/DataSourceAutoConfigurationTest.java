package org.apache.camel.component.sql;

import javax.sql.DataSource;

import org.apache.camel.CamelContext;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(SpringRunner.class)
@SpringBootApplication
@DirtiesContext
@ContextConfiguration(classes = DataSourceAutoConfigurationTest.class)
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:dummy://localhost/test",
        "spring.datasource.username=dbuser",
        "spring.datasource.password=dbpass",
        "spring.datasource.driver-class-name=org.apache.camel.component.sql.support.DummyJDBCDriver"
})
public class DataSourceAutoConfigurationTest {

    @Autowired
    private DataSource datasource;

    @Autowired
    private CamelContext context;

    @Test
    public void testInjectionWorks() {
        assertNotNull(datasource);
    }

    @Test
    public void testCamelUsesTheConfiguredDatasource() throws Exception {
        SqlComponent component = (SqlComponent) context.getComponent("sql");
        SqlEndpoint endpoint = (SqlEndpoint) component.createEndpoint("sql:select * from table where id=#");
        assertEquals(datasource, endpoint.getJdbcTemplate().getDataSource());
    }

}
