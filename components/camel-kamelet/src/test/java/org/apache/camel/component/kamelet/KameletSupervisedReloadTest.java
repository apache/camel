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
package org.apache.camel.component.kamelet;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.ServiceStatus;
import org.apache.camel.api.management.ManagedCamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.SupervisingRouteController;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * CAMEL-24630: reloading kamelet routes under a supervising route controller must not leave duplicate route entries or
 * break {@code ManagedCamelContext.getStartedRoutes()}.
 */
public class KameletSupervisedReloadTest extends CamelTestSupport {

    private static final int INITIAL_DELAY = 200;

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return routes();
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    void supervisedKameletReloadDoesNotDuplicateRoutesOrBreakManagement() throws Exception {
        SupervisingRouteController supervising = context.getRouteController().supervising();
        supervising.setInitialDelay(INITIAL_DELAY);

        context.addRoutes(routes());
        context.start();

        ManagedCamelContext managed = context.getCamelContextExtension().getContextPlugin(ManagedCamelContext.class);

        for (int reload = 0; reload <= 2; reload++) {
            Thread.sleep(INITIAL_DELAY + 300L);
            assertReloadState(reload, supervising, managed);
            if (reload == 2) {
                break;
            }
            reloadRoutes();
        }
    }

    private void assertReloadState(int reload, SupervisingRouteController supervising, ManagedCamelContext managed) {
        int routeCount = context.getRoutesSize();
        assertThat(routeCount).as("route count after reload %s", reload).isLessThanOrEqualTo(2);

        assertThat(context.getRouteIds()).as("unique route ids after reload %s", reload).hasSize(routeCount);

        for (var route : context.getRoutes()) {
            ServiceStatus status = supervising.getRouteStatus(route.getId());
            assertThat(status).as("status for route %s after reload %s", route.getId(), reload).isNotNull();
        }

        assertThat(supervising.getControlledRoutes()).as("controlled routes after reload %s", reload)
                .allMatch(route -> supervising.getRouteStatus(route.getId()) != null);

        assertThatCode(() -> {
            Integer started = managed.getManagedCamelContext().getStartedRoutes();
            assertThat(started).as("started routes after reload %s", reload).isNotNull().isGreaterThanOrEqualTo(0);
        }).doesNotThrowAnyException();
    }

    private void reloadRoutes() throws Exception {
        SupervisingRouteController supervising = context.getRouteController().supervising();
        supervising.removeAllRoutes();
        context.removeRouteTemplates("*");
        context.getEndpointRegistry().clear();
        context.addRoutes(routes());
        supervising.startRoutes(true);
    }

    private static RouteBuilder routes() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                routeTemplate("probe-source")
                        .from("timer:probe?repeatCount=1&delay=10")
                        .setBody(constant("hello"))
                        .to("kamelet:sink");

                from("kamelet:probe-source").routeId("probe-parent")
                        .process(exchange -> {
                        });
            }
        };
    }
}
