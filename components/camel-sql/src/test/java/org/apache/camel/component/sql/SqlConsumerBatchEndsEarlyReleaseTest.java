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
package org.apache.camel.component.sql;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.ExchangeFactory;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A batch of three rows that ends with an error after its first row (transacted, breakBatchOnConsumeFail): the
 * exchanges of the two rows that were not routed are released too.
 */
class SqlConsumerBatchEndsEarlyReleaseTest extends CamelTestSupport {

    private EmbeddedDatabase db;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        // count the exchanges the consumers create and release (enabled before the consumers are created)
        camelContext.getCamelContextExtension().getExchangeFactory().setStatisticsEnabled(true);
        return camelContext;
    }

    @Override
    public void doPreSetup() throws Exception {
        db = new EmbeddedDatabaseBuilder()
                .setName(getClass().getSimpleName())
                .setType(EmbeddedDatabaseType.H2)
                .addScript("sql/createAndPopulateDatabase.sql").build();
    }

    @Override
    public void doPostTearDown() throws Exception {
        if (db != null) {
            db.shutdown();
        }
    }

    @Test
    void testTransactedFailureReleasesTheRowsNotRouted() throws Exception {
        pollOnceAndCheckReleased("transacted", "mock:transacted");
    }

    @Test
    void testBreakBatchOnConsumeFailReleasesTheRowsNotRouted() throws Exception {
        pollOnceAndCheckReleased("break", "mock:break");
    }

    private void pollOnceAndCheckReleased(String routeId, String mockUri) throws Exception {
        MockEndpoint mock = getMockEndpoint(mockUri);
        mock.expectedBodiesReceived("Camel");

        // polled from the test: the route is synchronous, so the exchanges are done when poll returns or throws
        SqlConsumer consumer = (SqlConsumer) context.getRoute(routeId).getConsumer();
        assertThrows(Exception.class, consumer::poll);

        mock.assertIsSatisfied();
        // the exchange of the query, the one of the routed row and those of the two rows that were not routed
        ExchangeFactory.Statistics stats = context.getCamelContextExtension().getExchangeFactoryManager().getStatistics();
        assertEquals(4, stats.getCreatedCounter() + stats.getAcquiredCounter());
        assertEquals(4, stats.getReleasedCounter());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                getContext().getComponent("sql", SqlComponent.class).setDataSource(db);

                // the first row fails, and a transacted consumer breaks out of the batch
                from("sql:select * from projects order by id?startScheduler=false&transacted=true")
                        .routeId("transacted")
                        .setBody(simple("${body[project]}"))
                        .to("mock:transacted")
                        .throwException(new IllegalStateException("Forced"));

                // the onConsume query of the first row updates no row, and breakBatchOnConsumeFail breaks out of the batch
                from("sql:select * from projects order by id?startScheduler=false&breakBatchOnConsumeFail=true"
                     + "&onConsume=update projects set license = 'ASF' where id = -1&expectedUpdateCount=1")
                        .routeId("break")
                        .setBody(simple("${body[project]}"))
                        .to("mock:break");
            }
        };
    }
}
