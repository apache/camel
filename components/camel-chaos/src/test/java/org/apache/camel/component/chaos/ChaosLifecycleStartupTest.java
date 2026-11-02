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
package org.apache.camel.component.chaos;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * Lifecycle rules of {@code camel.chaos.rules}: their route is checked once the context created its routes (an unknown
 * route stops the context from starting), and they act when the context has started. With profile {@code prod} they are
 * ignored, as every rule is. Under the supervising route controller a route stopped by chaos stays stopped until chaos
 * starts it again.
 */
class ChaosLifecycleStartupTest {

    private CamelContext context;

    @AfterEach
    void stop() {
        if (context != null) {
            context.stop();
        }
    }

    private CamelContext create(String profile, Map<String, String> properties, boolean supervising) throws Exception {
        context = new DefaultCamelContext();
        context.getCamelContextExtension().setProfile(profile);
        properties.forEach(context.getPropertiesComponent()::addInitialProperty);
        if (supervising) {
            context.getRouteController().supervising().setInitialDelay(0);
        }
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("start").to("mock:target");
            }
        });
        return context;
    }

    private ServiceStatus status() {
        return context.getRouteController().getRouteStatus("start");
    }

    private JsonObject lifecycle(ChaosManager chaos) {
        JsonObject rule = (JsonObject) ((JsonArray) chaos.report().toJson().get("rules")).get(0);
        return (JsonObject) rule.get("lifecycle");
    }

    @Test
    void aStartupRuleActsWhenTheContextHasStarted() throws Exception {
        create(null, Map.of("camel.chaos.enabled", "true", "camel.chaos.rules", "route=start stall duration=300ms"), false);
        context.start();
        ChaosManager chaos = ChaosManager.get(context);
        await().atMost(10, TimeUnit.SECONDS).until(() -> "back".equals(lifecycle(chaos).getString("state")));
        assertThat(status()).isEqualTo(ServiceStatus.Started);
        assertThat(lifecycle(chaos).getLong("backAt") - lifecycle(chaos).getLong("downAt")).isGreaterThanOrEqualTo(300L);
    }

    @Test
    void aStartupRuleWaitsWhileTheContextIsStopped() throws Exception {
        create(null, Map.of("camel.chaos.enabled", "true", "camel.chaos.rules", "route=start stall duration=10m"), false);
        context.build();
        context.init();
        ChaosManager chaos = ChaosManager.get(context);
        // the routes exist, the context has not started: the rule waits
        assertThat(lifecycle(chaos).getString("state")).isEqualTo("pending");
        assertThat(chaos.lifecycle().hasExecutor()).isFalse();
        assertThat(chaos.report().toText()).contains("pending: suspend route start for 10m0s when the context has started");
        context.start();
        await().atMost(10, TimeUnit.SECONDS).until(() -> status() == ServiceStatus.Suspended);
    }

    @Test
    void aStartupRuleForAnUnknownRouteStopsTheContextFromStarting() throws Exception {
        create(null, Map.of("camel.chaos.enabled", "true", "camel.chaos.rules", "route=nope stall duration=1s"), false);
        assertThatThrownBy(() -> context.start())
                .hasStackTraceContaining("camel-chaos: Invalid chaos rule [route=nope stall duration=1s]: there is no route"
                                         + " nope");
        assertThat(context.isStarted()).isFalse();
    }

    @Test
    void aStartupRuleThatIsNotValidStopsTheContextFromStarting() throws Exception {
        create(null, Map.of("camel.chaos.enabled", "true", "camel.chaos.rules",
                "route=start stall duration=1s until=10"), false);
        assertThatThrownBy(() -> context.start()).hasStackTraceContaining("until= does not apply to the fault stall");
    }

    @Test
    void ignoredWithProfileProd() throws Exception {
        create("prod", Map.of("camel.chaos.enabled", "true", "camel.chaos.rules", "route=start stall duration=1s"), false);
        context.start();
        // nothing of chaos is installed, so nothing can stall the route
        assertThat(ChaosManager.get(context)).isNull();
        assertThat(status()).isEqualTo(ServiceStatus.Started);
    }

    @Test
    void aRouteStoppedByChaosStaysStoppedUnderTheSupervisingRouteController() throws Exception {
        create(null, Map.of("camel.chaos.enabled", "true"), true);
        context.start();
        await().atMost(10, TimeUnit.SECONDS).until(() -> status() == ServiceStatus.Started);
        ChaosManager chaos = ChaosManager.get(context);
        chaos.addRule("route=start lifecycle action=stop duration=3s");
        await().atMost(10, TimeUnit.SECONDS).until(() -> "down".equals(lifecycle(chaos).getString("state")));
        // a stop through the route controller is a manual stop for the supervising controller: it releases the route
        assertThat(status()).isEqualTo(ServiceStatus.Stopped);
        assertThat(context.getRoute("start").getRouteController()).isNull();
        await().atMost(10, TimeUnit.SECONDS).until(() -> "back".equals(lifecycle(chaos).getString("state")));
        assertThat(status()).isEqualTo(ServiceStatus.Started);
        // started through the supervising controller, which supervises the route again
        assertThat(context.getRoute("start").getRouteController())
                .isSameAs(context.getRouteController().supervising());
    }

    @Test
    void aStallUnderTheSupervisingRouteControllerResumesTheRoute() throws Exception {
        create(null, Map.of("camel.chaos.enabled", "true"), true);
        context.start();
        await().atMost(10, TimeUnit.SECONDS).until(() -> status() == ServiceStatus.Started);
        ChaosManager chaos = ChaosManager.get(context);
        chaos.addRule("route=start stall duration=300ms");
        await().atMost(10, TimeUnit.SECONDS).until(() -> "back".equals(lifecycle(chaos).getString("state")));
        assertThat(status()).isEqualTo(ServiceStatus.Started);
        assertThat(context.getRoute("start").getRouteController())
                .isSameAs(context.getRouteController().supervising());
    }

    @Test
    void aRemovedRouteIsAddedBackUnderTheSupervisingRouteController() throws Exception {
        create(null, Map.of("camel.chaos.enabled", "true"), true);
        context.start();
        await().atMost(10, TimeUnit.SECONDS).until(() -> status() == ServiceStatus.Started);
        ChaosManager chaos = ChaosManager.get(context);
        chaos.addRule("route=start lifecycle action=remove duration=300ms");
        await().atMost(10, TimeUnit.SECONDS).until(() -> "back".equals(lifecycle(chaos).getString("state")));
        // the supervising controller does not start the route that is added again by itself: chaos starts it through
        // the controller, which supervises it again
        assertThat(status()).isEqualTo(ServiceStatus.Started);
        assertThat(lifecycle(chaos).containsKey("reason")).isFalse();
        assertThat(context.getRoute("start").getRouteController())
                .isSameAs(context.getRouteController().supervising());
    }

    @Test
    void aStartupRuleRemovedBeforeTheContextStartsDoesNothing() throws Exception {
        create(null, Map.of("camel.chaos.enabled", "true", "camel.chaos.rules", "route=start stall duration=10m"), false);
        context.build();
        context.init();
        ChaosManager chaos = ChaosManager.get(context);
        assertThat(chaos.removeRule("r1")).isTrue();
        assertThat(lifecycle(chaos).getString("state")).isEqualTo("cancelled");
        assertThat(lifecycle(chaos).getString("reason")).isEqualTo("the rule was removed before it acted");
        context.start();
        assertThat(status()).isEqualTo(ServiceStatus.Started);
        assertThat(chaos.lifecycle().hasExecutor()).isFalse();
    }
}
