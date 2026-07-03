/*
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

import java.lang.reflect.Field;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import ch.vorburger.mariadb4j.DB;
import ch.vorburger.mariadb4j.DBConfigurationBuilder;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sql.stored.template.TemplateParser;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * This test evaluates SQL stored function call with sql-stored component against a MariaDB database because HSQLDB
 * doesn't support SQL stored functions.
 */
@EnabledOnOs(value = { OS.LINUX, OS.WINDOWS, OS.MAC },
             architectures = { "amd64", "x86_64", "aarch64", "aarch_64" })
@DisabledOnOs(value = OS.LINUX, architectures = { "aarch64", "aarch_64" },
              disabledReason = "MariaDB4j has no Linux ARM64 native binary (mariadb4j-db-linux64 is x86_64 only)")
public class SqlFunctionDataSourceTest extends CamelTestSupport {

    private static final String DB_NAME = "test";
    private static final Object DB_LOCK = new Object();
    private static DB sharedMariaDb;
    private static DataSource sharedDataSource;

    private DataSource db;
    private static JdbcTemplate jdbcTemplate;
    private TemplateParser templateParser;

    /**
     * MariaDB startup timeout in milliseconds. The default 30s in MariaDB4j is too short for CI environments under
     * load, where InnoDB initialization can take longer. 120s provides a comfortable margin.
     */
    private static final int MARIADB_START_TIMEOUT_MS = 120_000;

    /** Initialize the shared MariaDB database for the tests. */
    private static void initSharedMariaDb() throws Exception {
        DBConfigurationBuilder config = DBConfigurationBuilder.newBuilder();
        config.setPort(0);
        DB db = DB.newEmbeddedDB(config.build());

        // Increase the startup timeout from the default 30s — CI machines under load
        // may need more time for InnoDB initialization before "ready for connections"
        Field timeoutField = DB.class.getDeclaredField("dbStartMaxWaitInMS");
        timeoutField.setAccessible(true);
        timeoutField.setInt(db, MARIADB_START_TIMEOUT_MS);

        db.start();
        // Only assign to static field after successful start, so that surefire retries
        // will re-attempt initialization if start() fails
        sharedMariaDb = db;

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.mariadb.jdbc.Driver");
        dataSource.setUrl(sharedMariaDb.getConfiguration().getURL(DB_NAME));
        dataSource.setUsername("root");
        dataSource.setPassword("");
        sharedDataSource = dataSource;

        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(new ClassPathResource("sql/storedFunctionMariaDB.sql"));
        populator.setSeparator("$$");
        populator.execute(sharedDataSource);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (sharedMariaDb != null) {
                try {
                    sharedMariaDb.stop();
                } catch (Exception e) {
                    // best-effort cleanup on JVM shutdown
                }
            }
        }));
        jdbcTemplate = new JdbcTemplate(sharedDataSource);
    }

    /** Initialize the database for the tests. */
    @Override
    public void doPreSetup() throws Exception {
        synchronized (DB_LOCK) {
            if (sharedMariaDb == null) {
                initSharedMariaDb();
            }
        }
        db = sharedDataSource;
    }

    @Override
    public void doPostTearDown() throws Exception {
        db = null;
    }

    @BeforeEach
    void setupTest() {
        templateParser = new TemplateParser(context().getClassResolver());
    }

    /* Testing SQL stored function call with sql-stored component against
       MariaDB database.
    */
    @Test
    public void shouldExecuteStoredFunction() throws InterruptedException {
        MockEndpoint mock = getMockEndpoint("mock:query");
        mock.expectedMessageCount(1);

        Map<String, Object> headers = new HashMap<>();
        headers.put("num1", 11);
        headers.put("num2", 3);
        template.requestBodyAndHeaders("direct:query", null, headers);

        MockEndpoint.assertIsSatisfied(context);

        Exchange exchange = mock.getExchanges().get(0);
        assertEquals(Integer.valueOf(8), exchange.getIn().getBody(Map.class).get("resultofsub"));
    }

    /* Test moved from CallableStatementWrapperTest to here as HSQLDB doesn't
       support SQL stored functions. This test evaluates SQL stored function
       call using CallableStatementWrapper against a MariaDB database.
    */
    @Test
    public void shouldExecuteStoredFunctionDirect() throws Exception {
        CallableStatementWrapperFactory factory = new CallableStatementWrapperFactory(jdbcTemplate, templateParser, true);

        CallableStatementWrapper wrapper = new CallableStatementWrapper(
                "SUBNUMBERS_FUNCTION(OUT INTEGER resultofsub, INTEGER ${header.v1},INTEGER ${header.v2})",
                factory);

        final Exchange exchange = createExchangeWithBody(null);
        exchange.getIn().setHeader("v1", 12);
        exchange.getIn().setHeader("v2", 5);

        wrapper.call(new WrapperExecuteCallback() {
            @Override
            public void execute(StatementWrapper statementWrapper) throws SQLException, DataAccessException {
                statementWrapper.populateStatement(null, exchange);

                Map resultOfQuery = (Map) statementWrapper.executeStatement();
                assertEquals(7, resultOfQuery.get("resultofsub"));
            }
        });
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                getContext().getComponent("sql-stored", SqlStoredComponent.class).setDataSource(db);

                from("direct:query")
                        .to("sql-stored:SUBNUMBERS_FUNCTION(OUT INTEGER resultofsub, INTEGER ${header.num1},INTEGER ${header.num2})?function=true")
                        .to("mock:query");
            }
        };
    }
}
