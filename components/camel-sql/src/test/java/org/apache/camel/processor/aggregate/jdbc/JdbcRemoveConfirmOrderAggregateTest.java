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

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.fail;

public class JdbcRemoveConfirmOrderAggregateTest extends AbstractJdbcAggregationTestSupport {

    public static class SlowCommitDataSourceTransactionManager extends DataSourceTransactionManager {
        int count;

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            if ("main".equals(Thread.currentThread().getName()) && ++count == 2) {
                try {
                    LOG.debug("sleeping while committing...");
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            super.doCommit(status);
        }
    }

    public static class MyAggregationStrategyWithDelay extends MyAggregationStrategy implements AggregationStrategy {

        @Override
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            try {
                // The recovery thread has an initial delay of 1 sec
                LOG.debug("Delaying during aggregate");
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return super.aggregate(oldExchange, newExchange);
        }

        @Override
        public void onCompletion(Exchange oldExchange) {
            JdbcRemoveConfirmOrderAggregateTest.completedExchangeCount++;
        }
    }

    static int completedExchangeCount;
    private static final Logger LOG = LoggerFactory.getLogger(JdbcRemoveConfirmOrderAggregateTest.class.getName());

    @Override
    void configureJdbcAggregationRepository() {
        repo = applicationContext.getBean("repoSlowCommit", JdbcAggregationRepository.class);
        // enable recovery
        repo.setUseRecovery(true);
        // check faster
        repo.setRecoveryInterval(400, TimeUnit.MILLISECONDS);
    }

    @Test
    public void testJdbcAggregateRecover() throws Exception {

        getMockEndpoint("mock:result").expectedBodiesReceived("AB");

        template.sendBodyAndHeader("direct:start", "A", "id", 123);
        template.sendBodyAndHeader("direct:start", "B", "id", 123);
        MockEndpoint.assertIsSatisfied(context, 10, TimeUnit.SECONDS);
        // Wait until the recovery has been run
        await().atMost(500, TimeUnit.MILLISECONDS).until(this::checkCompletedNotPresent);
        Assertions.assertEquals(1, JdbcRemoveConfirmOrderAggregateTest.completedExchangeCount,
                "There should be only 1 completed aggregation");
    }

    private boolean checkCompletedNotPresent() {
        DataSource datasource = applicationContext.getBean("JdbcRemoveConfirmOrderAggregateTest-dataSourceSlow",
                DataSource.class);
        try {
            Connection connection = datasource.getConnection();
            ResultSet rs = connection.createStatement()
                    .executeQuery("SELECT * FROM aggregationRepo1_completed");
            return !rs.next();
        } catch (SQLException e) {
            fail(e);
            return false;
        }

    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").
                threads(2).
                transacted("required").aggregate(header("id"), new MyAggregationStrategyWithDelay()).completionSize(2).aggregationRepository(repo)
                    .optimisticLocking().to("mock:result").end();
            }
        };
    }
}
