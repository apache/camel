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

import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.OptimisticLockingAggregationRepository;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.apache.camel.util.ObjectHelper;
import org.springframework.context.support.AbstractApplicationContext;

public abstract class AbstractJdbcAggregationTestSupport extends CamelSpringTestSupport {

    JdbcAggregationRepository repo;

    @Override
    public void postProcessTest() throws Exception {
        super.postProcessTest();

        repo = applicationContext.getBean("repo1", JdbcAggregationRepository.class);
        configureJdbcAggregationRepository();
    }

    void configureJdbcAggregationRepository() {
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            // START SNIPPET: e1
            public void configure() {
                // here is the Camel route where we aggregate
                from("direct:start")
                        .aggregate(header("id"), new MyAggregationStrategy())
                        // complete every 5th seconds
                        .completionInterval(getCompletionInterval()).aggregationRepository(repo)
                        .to("mock:aggregated");
            }
            // END SNIPPET: e1
        };
    }

    long getCompletionInterval() {
        return 5000;
    }

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return newAppContext(
                "JdbcSpringDataSource.xml", "JdbcSpringDataSource.xml");
    }

    protected Exchange repoAddAndGet(String key, Exchange exchange) {
        return repoAddAndGet(key, exchange, true);
    }

    protected Exchange repoAddAndGet(String key, Exchange exchange, boolean optimistic) {
        int retry = optimistic ? 5 : 1;
        while (retry-- > 0) {
            try {
                repo.add(context, key, exchange);
                // recover the exchange with the new version to be able to add again
                exchange = repo.get(context, key);
                return exchange;
            } catch (Exception e) {
                if (optimistic) {
                    OptimisticLockingAggregationRepository.OptimisticLockingException ole
                            = ObjectHelper.getException(OptimisticLockingAggregationRepository.OptimisticLockingException.class,
                                    e);
                    if (ole != null) {
                        // okay lets try again
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException ex) {
                            // ignore
                        }
                        continue;
                    }
                }
                throw e;
            }
        }
        return exchange;
    }

    public static class MyAggregationStrategy implements AggregationStrategy {

        @Override
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            if (oldExchange == null) {
                return newExchange;
            }
            String body1 = oldExchange.getIn().getBody(String.class);
            String body2 = newExchange.getIn().getBody(String.class);

            oldExchange.getIn().setBody(body1 + body2);
            return oldExchange;
        }
    }
}
