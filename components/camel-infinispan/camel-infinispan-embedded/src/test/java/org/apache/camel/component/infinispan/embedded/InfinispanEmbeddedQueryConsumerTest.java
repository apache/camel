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

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.infinispan.InfinispanConstants;
import org.apache.camel.component.infinispan.InfinispanQueryBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.infinispan.protostream.sampledomain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class InfinispanEmbeddedQueryConsumerTest extends InfinispanEmbeddedQueryTestSupport {

    @BindToRegistry("continuousQueryBuilder")
    private InfinispanQueryBuilder continuousQueryBuilder = InfinispanQueryBuilder.create(
            "FROM org.infinispan.protostream.sampledomain.User WHERE name like 'CQ%'");

    @BindToRegistry("continuousQueryBuilderNoMatch")
    private InfinispanQueryBuilder continuousQueryBuilderNoMatch = InfinispanQueryBuilder.create(
            "FROM org.infinispan.protostream.sampledomain.User WHERE name like '%TEST%'");

    @BindToRegistry("continuousQueryBuilderAll")
    private InfinispanQueryBuilder continuousQueryBuilderAll = InfinispanQueryBuilder.create(
            "FROM org.infinispan.protostream.sampledomain.User WHERE name like '%Q0%'");

    // *****************************
    //
    // *****************************

    @Test
    public void continuousQuery() throws Exception {
        MockEndpoint continuousQueryBuilderNoMatch = getMockEndpoint("mock:continuousQueryNoMatch");
        continuousQueryBuilderNoMatch.expectedMessageCount(0);

        MockEndpoint continuousQueryBuilderAll = getMockEndpoint("mock:continuousQueryAll");
        continuousQueryBuilderAll.expectedMessageCount(CQ_USERS.length * 2);

        MockEndpoint continuousQuery = getMockEndpoint("mock:continuousQuery");
        continuousQuery.expectedMessageCount(4);

        for (int i = 0; i < 4; i++) {
            continuousQuery.message(i)
                    .header(InfinispanConstants.KEY)
                    .isEqualTo(createKey(CQ_USERS[i % 2]));

            continuousQuery
                    .message(i)
                    .header(InfinispanConstants.CACHE_NAME).isEqualTo(getCache().getName());

            if (i >= 2) {
                continuousQuery.message(i)
                        .header(InfinispanConstants.EVENT_TYPE)
                        .isEqualTo(InfinispanConstants.CACHE_ENTRY_LEAVING);
                continuousQuery.message(i)
                        .header(InfinispanConstants.EVENT_DATA)
                        .isNull();
            } else {
                continuousQuery.message(i)
                        .header(InfinispanConstants.EVENT_TYPE)
                        .isEqualTo(InfinispanConstants.CACHE_ENTRY_JOINING);
                continuousQuery.message(i)
                        .header(InfinispanConstants.EVENT_DATA)
                        .isNotNull();
                continuousQuery.message(i)
                        .header(InfinispanConstants.EVENT_DATA)
                        .isInstanceOf(User.class);
            }
        }

        for (final User user : CQ_USERS) {
            getCache().put(createKey(user), user);
        }

        assertEquals(CQ_USERS.length, getCache().size());

        for (final User user : CQ_USERS) {
            getCache().remove(createKey(user));
        }

        assertTrue(getCache().isEmpty());

        continuousQuery.assertIsSatisfied();
        continuousQueryBuilderNoMatch.assertIsSatisfied();
        continuousQueryBuilderAll.assertIsSatisfied();
    }

    // *****************************
    //
    // *****************************

    @BeforeEach
    protected void beforeEach() {
        // cleanup the default test cache before each run
        getCache().clear();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                fromF("infinispan-embedded:%s?queryBuilder=#continuousQueryBuilder", getCacheName())
                        .to("mock:continuousQuery");
                fromF("infinispan-embedded:%s?queryBuilder=#continuousQueryBuilderNoMatch", getCacheName())
                        .to("mock:continuousQueryNoMatch");
                fromF("infinispan-embedded:%s?queryBuilder=#continuousQueryBuilderAll", getCacheName())
                        .to("mock:continuousQueryAll");
            }
        };
    }
}
