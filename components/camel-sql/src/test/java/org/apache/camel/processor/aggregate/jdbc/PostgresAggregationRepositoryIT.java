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
package org.apache.camel.processor.aggregate.jdbc;

import java.util.Set;

import javax.sql.DataSource;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.test.infra.postgres.services.PostgresService;
import org.apache.camel.test.infra.postgres.services.PostgresServiceFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link PostgresAggregationRepository} against a real PostgreSQL database.
 */
public class PostgresAggregationRepositoryIT {

    private static final String REPO_NAME = "camel_agg_it";
    private static final String REPO_NAME_TEXT = "camel_agg_text_it";

    @RegisterExtension
    public static PostgresService service = PostgresServiceFactory.createService();

    private CamelContext context;
    private DataSource dataSource;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    public void setUp() throws Exception {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(service.jdbcUrl());
        ds.setUser(service.userName());
        ds.setPassword(service.password());
        dataSource = ds;

        jdbcTemplate = new JdbcTemplate(dataSource);
        for (String table : new String[] { REPO_NAME, REPO_NAME + "_completed" }) {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS " + table
                                 + " (id VARCHAR(255) NOT NULL PRIMARY KEY, exchange BYTEA NOT NULL, version BIGINT NOT NULL)");
            jdbcTemplate.execute("TRUNCATE TABLE " + table);
        }
        for (String table : new String[] { REPO_NAME_TEXT, REPO_NAME_TEXT + "_completed" }) {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS " + table
                                 + " (id VARCHAR(255) NOT NULL PRIMARY KEY, exchange BYTEA NOT NULL, version BIGINT NOT NULL, body TEXT)");
            jdbcTemplate.execute("TRUNCATE TABLE " + table);
        }

        context = new DefaultCamelContext();
        context.start();
    }

    @AfterEach
    public void tearDown() {
        if (context != null) {
            context.stop();
        }
    }

    private PostgresAggregationRepository createRepository(String repositoryName) {
        return new PostgresAggregationRepository(
                new DataSourceTransactionManager(dataSource), repositoryName, dataSource);
    }

    @Test
    public void testAddGetUpdateRemoveRecoverConfirm() throws Exception {
        PostgresAggregationRepository repo = createRepository(REPO_NAME);
        ServiceHelper.startService(repo);
        try {
            // insert a new aggregation
            Exchange exchange = new DefaultExchange(context);
            exchange.getIn().setBody("counter:1");
            assertNull(repo.add(context, "foo", exchange));

            // read it back
            Exchange actual = repo.get(context, "foo");
            assertNotNull(actual);
            assertEquals("counter:1", actual.getIn().getBody());

            // update it using the version loaded by get()
            actual.getIn().setBody("counter:2");
            repo.add(context, "foo", actual);
            actual = repo.get(context, "foo");
            assertEquals("counter:2", actual.getIn().getBody());

            // complete the aggregation: moves it to the completed table
            repo.remove(context, "foo", actual);
            assertNull(repo.get(context, "foo"));

            Set<String> completed = repo.scan(context);
            assertEquals(1, completed.size());

            String exchangeId = completed.iterator().next();
            Exchange recovered = repo.recover(context, exchangeId);
            assertNotNull(recovered);
            assertEquals("counter:2", recovered.getIn().getBody());

            // confirm deletes it from the completed table
            repo.confirm(context, exchangeId);
            assertTrue(repo.scan(context).isEmpty());
        } finally {
            ServiceHelper.stopService(repo);
        }
    }

    @Test
    public void testStoreBodyAsText() throws Exception {
        PostgresAggregationRepository repo = createRepository(REPO_NAME_TEXT);
        repo.setStoreBodyAsText(true);
        ServiceHelper.startService(repo);
        try {
            Exchange exchange = new DefaultExchange(context);
            exchange.getIn().setBody("myBody");
            repo.add(context, "foo", exchange);

            // the body column must contain the message body as text
            String body = jdbcTemplate.queryForObject(
                    "SELECT body FROM " + REPO_NAME_TEXT + " WHERE id = ?", String.class, "foo");
            assertEquals("myBody", body);

            Exchange actual = repo.get(context, "foo");
            assertEquals("myBody", actual.getIn().getBody());
        } finally {
            ServiceHelper.stopService(repo);
        }
    }
}
