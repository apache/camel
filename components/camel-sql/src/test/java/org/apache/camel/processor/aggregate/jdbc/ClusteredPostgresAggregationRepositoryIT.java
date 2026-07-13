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
 * Verifies {@link ClusteredPostgresAggregationRepository} against a real PostgreSQL database, including instance-scoped
 * recovery where each cluster node must only recover the exchanges it completed itself.
 */
public class ClusteredPostgresAggregationRepositoryIT {

    private static final String REPO_NAME = "camel_clu_agg_it";

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
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS " + REPO_NAME
                             + " (id VARCHAR(255) NOT NULL PRIMARY KEY, exchange BYTEA NOT NULL, version BIGINT NOT NULL)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS " + REPO_NAME + "_completed"
                             + " (id VARCHAR(255) NOT NULL PRIMARY KEY, exchange BYTEA NOT NULL, version BIGINT NOT NULL,"
                             + " instance_id VARCHAR(255))");
        jdbcTemplate.execute("TRUNCATE TABLE " + REPO_NAME);
        jdbcTemplate.execute("TRUNCATE TABLE " + REPO_NAME + "_completed");

        context = new DefaultCamelContext();
        context.start();
    }

    @AfterEach
    public void tearDown() {
        if (context != null) {
            context.stop();
        }
    }

    private ClusteredPostgresAggregationRepository createRepository(String instanceId) {
        ClusteredPostgresAggregationRepository repo = new ClusteredPostgresAggregationRepository(
                new DataSourceTransactionManager(dataSource), REPO_NAME, dataSource);
        repo.setInstanceId(instanceId);
        repo.setRecoveryByInstance(true);
        return repo;
    }

    @Test
    public void testInstanceScopedRecovery() throws Exception {
        ClusteredPostgresAggregationRepository repoA = createRepository("nodeA");
        ClusteredPostgresAggregationRepository repoB = createRepository("nodeB");
        ServiceHelper.startService(repoA, repoB);
        try {
            // nodeA aggregates an exchange
            Exchange exchange = new DefaultExchange(context);
            exchange.getIn().setBody("payload");
            assertNull(repoA.add(context, "foo", exchange));

            Exchange stored = repoA.get(context, "foo");
            assertNotNull(stored);
            assertEquals("payload", stored.getIn().getBody());

            // completing moves it to the completed table, tagged with nodeA's instance id
            repoA.remove(context, "foo", stored);
            assertNull(repoA.get(context, "foo"));

            String exchangeId = stored.getExchangeId();
            String instanceId = jdbcTemplate.queryForObject(
                    "SELECT instance_id FROM " + REPO_NAME + "_completed WHERE id = ?", String.class, exchangeId);
            assertEquals("nodeA", instanceId,
                    "completed exchange must be tagged with the instance id of the node that completed it");

            // only nodeA may recover it
            Set<String> scannedByA = repoA.scan(context);
            assertEquals(Set.of(exchangeId), scannedByA);
            assertTrue(repoB.scan(context).isEmpty(), "nodeB must not recover exchanges completed by nodeA");

            Exchange recovered = repoA.recover(context, exchangeId);
            assertNotNull(recovered);
            assertEquals("payload", recovered.getIn().getBody());

            // confirm deletes it from the completed table
            repoA.confirm(context, exchangeId);
            assertTrue(repoA.scan(context).isEmpty());
        } finally {
            ServiceHelper.stopService(repoA, repoB);
        }
    }
}
