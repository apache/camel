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
package org.apache.camel.impl.engine;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.UnitOfWork;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for {@link DefaultUnitOfWork#routeStackLevel(java.util.function.Predicate)} (CAMEL-24887).
 */
public class DefaultUnitOfWorkRouteStackLevelPredicateTest extends ContextTestSupport {

    @Test
    public void testEmptyStackReturnsZero() throws Exception {
        UnitOfWork uow = new DefaultUnitOfWork(createExchangeWithBody("test"));
        assertThat(uow.routeStackLevel(r -> true)).isZero();
    }

    @Test
    public void testSingleRouteMatchingGroup() throws Exception {
        UnitOfWork uow = new DefaultUnitOfWork(createExchangeWithBody("test"));
        Route routeA = context.getRoute("a");

        uow.pushRoute(routeA);

        assertThat(uow.routeStackLevel(r -> "alpha".equals(r.getGroup()))).isEqualTo(1);
        assertThat(uow.routeStackLevel(r -> "beta".equals(r.getGroup()))).isZero();
    }

    @Test
    public void testNestedRoutesInSameGroup() throws Exception {
        UnitOfWork uow = new DefaultUnitOfWork(createExchangeWithBody("test"));
        Route routeA = context.getRoute("a");
        Route routeB = context.getRoute("b");

        uow.pushRoute(routeA);
        uow.pushRoute(routeB);

        assertThat(uow.routeStackLevel(r -> "alpha".equals(r.getGroup()))).isEqualTo(2);
    }

    @Test
    public void testNestedRoutesDifferentGroups() throws Exception {
        UnitOfWork uow = new DefaultUnitOfWork(createExchangeWithBody("test"));
        Route routeA = context.getRoute("a");
        Route routeC = context.getRoute("c");

        uow.pushRoute(routeA);
        uow.pushRoute(routeC);

        assertThat(uow.routeStackLevel(r -> "alpha".equals(r.getGroup()))).isEqualTo(1);
        assertThat(uow.routeStackLevel(r -> "beta".equals(r.getGroup()))).isEqualTo(1);
    }

    @Test
    public void testPopReducesLevel() throws Exception {
        UnitOfWork uow = new DefaultUnitOfWork(createExchangeWithBody("test"));
        Route routeA = context.getRoute("a");
        Route routeB = context.getRoute("b");

        uow.pushRoute(routeA);
        uow.pushRoute(routeB);
        assertThat(uow.routeStackLevel(r -> "alpha".equals(r.getGroup()))).isEqualTo(2);

        uow.popRoute();
        assertThat(uow.routeStackLevel(r -> "alpha".equals(r.getGroup()))).isEqualTo(1);

        uow.popRoute();
        assertThat(uow.routeStackLevel(r -> "alpha".equals(r.getGroup()))).isZero();
    }

    @Test
    public void testRouteWithNoGroupDoesNotMatch() throws Exception {
        UnitOfWork uow = new DefaultUnitOfWork(createExchangeWithBody("test"));
        Route routeD = context.getRoute("d");

        uow.pushRoute(routeD);

        assertThat(uow.routeStackLevel(r -> "alpha".equals(r.getGroup()))).isZero();
        assertThat(uow.routeStackLevel(r -> r.getGroup() == null)).isEqualTo(1);
    }

    @Test
    public void testMixedGroupsAndNoGroup() throws Exception {
        UnitOfWork uow = new DefaultUnitOfWork(createExchangeWithBody("test"));
        Route routeA = context.getRoute("a");
        Route routeD = context.getRoute("d");
        Route routeC = context.getRoute("c");

        uow.pushRoute(routeA);
        uow.pushRoute(routeD);
        uow.pushRoute(routeC);

        assertThat(uow.routeStackLevel(r -> "alpha".equals(r.getGroup()))).isEqualTo(1);
        assertThat(uow.routeStackLevel(r -> "beta".equals(r.getGroup()))).isEqualTo(1);
        assertThat(uow.routeStackLevel(r -> r.getGroup() == null)).isEqualTo(1);
        assertThat(uow.routeStackLevel(r -> true)).isEqualTo(3);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:a").routeId("a").routeGroup("alpha").to("mock:a");
                from("direct:b").routeId("b").routeGroup("alpha").to("mock:b");
                from("direct:c").routeId("c").routeGroup("beta").to("mock:c");
                from("direct:d").routeId("d").to("mock:d");
            }
        };
    }
}
