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

import static org.apache.camel.component.mock.MockEndpoint.assertIsSatisfied;

import java.io.File;
import java.sql.DriverManager;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Registry;
import org.apache.camel.spring.spi.SpringTransactionPolicy;
import org.apache.camel.util.FileUtil;
import org.apache.derby.jdbc.EmbeddedDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Isolated
public class JdbcLoopTransactedSplitterStackSizeTest extends AbstractJdbcAggregationTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(JdbcLoopTransactedSplitterStackSizeTest.class);

    private static final boolean PRINT_STACK_TRACE = false;
    private int total = 500;

    private static final String DSNAME = "ds";
    private static EmbeddedDataSource ds;
    private static PlatformTransactionManager txManager;
    private static SpringTransactionPolicy txPolicy;

    private final String xmlBody =
            """
            <messages>
                <message><name>John</name></message>
                <message><name>Jane</name></message>
                <message><name>Jim</name></message>
                <message><name>Jack</name></message>
                <message><name>Jill</name></message>
            </messages>
            """;

    @Override
    protected void bindToRegistry(Registry registry) throws Exception {
        ds = Derby.init(DSNAME);

        txManager = new DataSourceTransactionManager(ds);

        TransactionTemplate txTemplate = new TransactionTemplate(txManager);
        txTemplate.setPropagationBehaviorName("PROPAGATION_REQUIRED");
        txTemplate.setIsolationLevelName("ISOLATION_READ_COMMITTED");
        txTemplate.setTimeout(1800);

        txPolicy = new SpringTransactionPolicy();
        txPolicy.setTransactionManager(txManager);
        txPolicy.setTransactionTemplate(txTemplate);

        registry.bind("txPolicy", txPolicy);
    }

    @AfterAll
    public static void tearDownOnce() {
        Derby.close("ds");
    }

    @Test
    public void testIfLoopCompleted() throws Exception {
        getMockEndpoint("mock:line").expectedMessageCount(total);
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", "");

        assertIsSatisfied(context);

        int[] sizes = new int[total + 1];
        for (int i = 0; i < total; i++) {
            int size = getMockEndpoint("mock:line")
                    .getReceivedExchanges()
                    .get(i)
                    .getMessage()
                    .getHeader("stackSize", int.class);
            sizes[i] = size;
            Assertions.assertTrue(size < 180, "Stackframe should be < 180, was: " + size);
            LOG.debug("#{} size {}", i, size);
        }
        int size = getMockEndpoint("mock:result")
                .getReceivedExchanges()
                .get(0)
                .getMessage()
                .getHeader("stackSize", int.class);
        sizes[total] = size;
        LOG.debug("#{} size {}", total, size);

        int prev = sizes[0];
        // last may be shorter, so use total - 1
        for (int i = 1; i < total - 1; i++) {
            size = sizes[i];
            Assertions.assertEquals(prev, size, "Stackframe should be same size");
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .loop(total / 5)
                        .log("Loop ${exchangeProperty.CamelLoopIndex}")
                        .setBody(simple(xmlBody))
                        .to("direct:subroute")
                        .end()
                        .log("Completed successfully ${exchangeId}")
                        .setHeader("stackSize", JdbcLoopTransactedSplitterStackSizeTest::currentStackSize)
                        .to("mock:result");

                from("direct:subroute")
                        .transacted("txPolicy")
                        .split(xpath("/messages/message"))
                        .streaming()
                        .stopOnException()
                        .setHeader("stackSize", JdbcLoopTransactedSplitterStackSizeTest::currentStackSize)
                        .log("Body === ${body}")
                        .to("mock:line")
                        .end();
            }
        };
    }

    public static int currentStackSize() {
        int depth = Thread.currentThread().getStackTrace().length;
        if (PRINT_STACK_TRACE) {
            new Throwable("Printing Stacktrace depth: " + depth).printStackTrace(System.err);
        }
        return depth;
    }

    private static class Derby {

        public static EmbeddedDataSource init(String db) {
            deleteDatabaseFiles(db);
            EmbeddedDataSource ds = new EmbeddedDataSource();
            ds.setDataSourceName(db);
            ds.setDatabaseName(db);
            ds.setConnectionAttributes("create=true");
            return ds;
        }

        public static void close(String dbName) {
            // unload the driver
            try {
                DriverManager.getConnection("jdbc:derby:;shutdown=true");
            } catch (Exception e) {
                // ignore
            }
            deleteDatabaseFiles(dbName);
        }

        private static void deleteDatabaseFiles(String dbName) {
            FileUtil.deleteFile(new File("derby.log"));
            FileUtil.removeDir(new File(dbName));
        }
    }
}
