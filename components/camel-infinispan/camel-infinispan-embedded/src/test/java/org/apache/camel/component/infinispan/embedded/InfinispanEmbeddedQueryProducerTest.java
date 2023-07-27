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
package org.apache.camel.component.infinispan.embedded;

import java.util.List;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.infinispan.InfinispanOperation;
import org.apache.camel.component.infinispan.InfinispanQueryBuilder;
import org.infinispan.protostream.sampledomain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.infinispan.InfinispanConstants.OPERATION;
import static org.apache.camel.component.infinispan.InfinispanConstants.QUERY_BUILDER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class InfinispanEmbeddedQueryProducerTest extends InfinispanEmbeddedQueryTestSupport {

    @BindToRegistry("noResultQueryBuilder")
    private InfinispanQueryBuilder noResultQueryBuilder = InfinispanQueryBuilder.create(
            "FROM org.infinispan.protostream.sampledomain.User WHERE name like '%abc'");

    @BindToRegistry("withResultQueryBuilder")
    private InfinispanQueryBuilder withResultQueryBuilder = InfinispanQueryBuilder.create(
            "FROM org.infinispan.protostream.sampledomain.User WHERE name like '%A'");

    // *****************************
    //
    // *****************************

    @Test
    public void producerQueryOperationWithoutQueryBuilder() {
        Exchange request = template.request("direct:start",
                exchange -> exchange.getIn().setHeader(OPERATION, InfinispanOperation.QUERY));
        assertNull(request.getException());

        List<User> queryResult = request.getIn().getBody(List.class);
        assertNull(queryResult);
    }

    @Test
    public void producerQueryWithoutResult() {
        producerQueryWithoutResult("direct:start", noResultQueryBuilder);
    }

    @Test
    public void producerQueryWithoutResultAndQueryBuilderFromConfig() {
        producerQueryWithoutResult("direct:noQueryResults", null);
    }

    private void producerQueryWithoutResult(String endpoint, final InfinispanQueryBuilder builder) {
        Exchange request = template.request(endpoint, createQueryProcessor(builder));

        assertNull(request.getException());

        List<User> queryResult = request.getIn().getBody(List.class);
        assertNotNull(queryResult);
        assertEquals(0, queryResult.size());
    }

    @Test
    public void producerQueryWithResult() {
        producerQueryWithResult("direct:start", withResultQueryBuilder);
    }

    @Test
    public void producerQueryWithResultAndQueryBuilderFromConfig() {
        producerQueryWithResult("direct:queryWithResults", null);
    }

    // *****************************
    //
    // *****************************

    @Override
    protected void setupResources() throws Exception {
        super.setupResources();
    }

    @BeforeEach
    protected void beforeEach() {
        // cleanup the default test cache before each run
        getCache().clear();

        for (final User user : USERS) {
            getCache().put(createKey(user), user);
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .toF("infinispan-embedded:%s", getCacheName());
                from("direct:noQueryResults")
                        .toF("infinispan-embedded:%s?queryBuilder=#noResultQueryBuilder", getCacheName());
                from("direct:queryWithResults")
                        .toF("infinispan-embedded:%s?queryBuilder=#withResultQueryBuilder", getCacheName());
            }
        };
    }

    private void producerQueryWithResult(String endpoint, final InfinispanQueryBuilder builder) {
        Exchange request = template.request(endpoint, createQueryProcessor(builder));
        assertNull(request.getException());

        List<User> queryResult = request.getIn().getBody(List.class);
        assertNotNull(queryResult);
        assertEquals(2, queryResult.size());
        assertTrue(hasUser(queryResult, "nameA", "surnameA"));
        assertTrue(hasUser(queryResult, "nameA", "surnameB"));
    }

    private Processor createQueryProcessor(final InfinispanQueryBuilder builder) {
        return exchange -> {
            exchange.getIn().setHeader(OPERATION, InfinispanOperation.QUERY);
            if (builder != null) {
                exchange.getIn().setHeader(QUERY_BUILDER, builder);
            }
        };
    }
}
