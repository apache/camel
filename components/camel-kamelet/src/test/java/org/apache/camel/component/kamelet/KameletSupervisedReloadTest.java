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

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.ServiceStatus;
import org.apache.camel.api.management.ManagedCamelContext;
import org.apache.camel.api.management.mbean.ManagedCamelContextMBean;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.SupervisingRouteController;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * CAMEL-24630: reloading kamelet routes under a supervising route controller must not leave duplicate route entries or
 * break {@code ManagedCamelContext.getStartedRoutes()}.
 */
public class KameletSupervisedReloadTest extends CamelTestSupport {

    private static final int INITIAL_DELAY = 200;

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Override
    protected boolean useJmx() {
        return true;
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getManagementStrategy().getManagementAgent().setRegisterRoutesCreateByKamelet(true);
        return context;
    }

    @Test
    void supervisedKameletReloadDoesNotDuplicateRoutesOrBreakManagement() throws Exception {
        SupervisingRouteController supervising = context.getRouteController().supervising();
        supervising.setInitialDelay(INITIAL_DELAY);

        context.addRoutes(routes());
        context.start();

        ManagedCamelContextMBean managed = resolveManagedCamelContextMBean();

        for (int i = 0; i <= 2; i++) {
            final int reload = i;
            awaitReloadStable(supervising);
            assertReloadState(reload, supervising, managed);
            if (reload == 2) {
                break;
            }
            reloadRoutes();
        }
    }

    private ManagedCamelContextMBean resolveManagedCamelContextMBean() {
        // Same lookup path as ContextDevConsole / JBang dev console (not direct MBean construction)
        ManagedCamelContext plugin = context.getCamelContextExtension().getContextPlugin(ManagedCamelContext.class);
        assertThat(plugin).isNotNull();
        ManagedCamelContextMBean managed = plugin.getManagedCamelContext();
        assertThat(managed).isNotNull();
        return managed;
    }

    private void awaitReloadStable(SupervisingRouteController supervising) {
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(context.getRoutesSize()).isEqualTo(2);
            for (String routeId : context.getRouteIds()) {
                ServiceStatus status = supervising.getRouteStatus(routeId);
                assertThat(status).isNotNull();
                assertThat(status.isStarted()).isTrue();
            }
        });
    }

    private void assertReloadState(int reload, SupervisingRouteController supervising, ManagedCamelContextMBean managed) {
        assertThat(context.getRoutesSize()).as("route count after reload %s", reload).isEqualTo(2);
        assertThat(context.getRouteIds()).as("unique route ids after reload %s", reload).hasSize(2);

        Set<String> routeIdsFromInstances = new HashSet<>();
        for (var route : context.getRoutes()) {
            assertThat(routeIdsFromInstances.add(route.getId()))
                    .as("duplicate route instance for id %s after reload %s", route.getId(), reload)
                    .isTrue();
            assertThat(supervising.getRouteStatus(route.getId()))
                    .as("status for route %s after reload %s", route.getId(), reload)
                    .isNotNull();
        }

        assertThat(supervising.getControlledRoutes()).as("controlled routes after reload %s", reload).hasSize(2);

        Integer started = managed.getStartedRoutes();
        assertThat(started).as("started routes after reload %s", reload).isEqualTo(2);
    }

    private void reloadRoutes() throws Exception {
        SupervisingRouteController supervising = context.getRouteController().supervising();
        supervising.removeAllRoutes();
        context.removeRouteTemplates("*");
        context.getEndpointRegistry().clear();
        context.addRoutes(routes());
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
