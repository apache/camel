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
package org.apache.camel.processor.aggregate.zipfile;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dataformat.zipfile.ZipSplitter;
import org.apache.camel.spring.spi.SpringTransactionPolicy;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ZipSplitAggregateTransactedIssueTest extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(ZipSplitAggregateTransactedIssueTest.class);

    String zipArchiveWithTwoFiles
            = "UEsDBBQAAAAIAFlrtFDFAfecUAAAAB4BAAALAAAAT3JkZXJzMS54bWyzyS9KSS0qtuPl4oQwQSxOm8wUOxMb/cwUCK+gKD+lNLkEzOG0yUvMTbWDCik42uiD+WB1+kgKbfThxqEZbEqUwU6kG2xGlMHOhA2GsortAFBLAwQUAAAACABBW9hQgBf0tVgAAAAqAQAACwAAAE9yZGVyczIueG1ss8kvSkktKrbj5eKEMEEsTpvMFDtDQ0Mb/cwUCL+gKD+lNLkEzOG0yUvMTbWDCimA1YFFwCr1kZTa6MONRDPcyMiIKMPB6kg13NjYmCjDweoIGQ5lFdsBAFBLAQIfABQAAAAIAFlrtFDFAfecUAAAAB4BAAALACQAAAAAAAAAIAAAAAAAAABPcmRlcnMxLnhtbAoAIAAAAAAAAQAYAAD57I2ZLtYBg97kuHn02gEA+eyNmS7WAVBLAQIfABQAAAAIAEFb2FCAF/S1WAAAACoBAAALACQAAAAAAAAAIAAAAHkAAABPcmRlcnMyLnhtbAoAIAAAAAAAAQAYAAAxPXoJStYBjn3iuHn02gEAMT16CUrWAVBLBQYAAAAAAgACALoAAAD6AAAAAAA=";

    @Test
    public void testIfAllSplitsAggregated() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");

        template.sendBody("direct:start", "");

        mock.assertIsSatisfied();

        // Check if second file was processed in aggregate() method of AggregationStrategy
        assertEquals("Orders2.xml", mock.getExchanges().get(0).getMessage().getHeader("CamelFileName", String.class));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                JdbcDataSource dataSource = new JdbcDataSource();
                dataSource.setURL("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1");
                dataSource.setUser("sa");
                dataSource.setPassword("");

                DataSourceTransactionManager txManager = new DataSourceTransactionManager(dataSource);

                TransactionTemplate transactionTemplate = new TransactionTemplate(txManager);
                transactionTemplate.setPropagationBehaviorName("PROPAGATION_REQUIRED");
                transactionTemplate.setIsolationLevelName("ISOLATION_READ_COMMITTED");
                transactionTemplate.setTimeout(1800);

                SpringTransactionPolicy springTransactionPolicy = new SpringTransactionPolicy();
                springTransactionPolicy.setTransactionManager(txManager);
                springTransactionPolicy.setTransactionTemplate(transactionTemplate);

                getContext().getRegistry().bind("transacted", springTransactionPolicy);
                getContext().getRegistry().bind("zipSplitter", new ZipSplitter());

                from("direct:start").streamCache(false)
                        .transacted("transacted")
                        .setBody().simple(zipArchiveWithTwoFiles)
                        .unmarshal().base64()
                        .split().ref("zipSplitter").aggregationStrategy(new StringAggregationStrategy())
                            .log("Splitting ${header.CamelFileName}")
                        .end()
                        .to("mock:result");
            }
        };
    }

    private static class StringAggregationStrategy implements AggregationStrategy {
        @Override
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            String name = newExchange.getMessage().getHeader("CamelFileName", String.class);
            LOG.info("Aggregating {}", name);
            return newExchange;
        }
    }
}
