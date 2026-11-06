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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rules of {@code camel.chaos.rules} start their clock when the context has started, so a slow startup does not
 * take from their until and within times. The time runs on a virtual clock (it is static, so this class runs alone).
 */
@Isolated
class ChaosStartupClockTest {

    private static final long STARTUP = TimeUnit.SECONDS.toMillis(30);

    private FakeClock clock;
    private CamelContext context;

    @BeforeEach
    void installClock() {
        clock = FakeClock.install();
    }

    @AfterEach
    void stop() {
        if (context != null) {
            context.stop();
        }
        clock.close();
    }

    private ChaosManager startSlowly(String rules) throws Exception {
        context = new DefaultCamelContext();
        Map.of("camel.chaos.enabled", "true", "camel.chaos.rules", rules)
                .forEach(context.getPropertiesComponent()::addInitialProperty);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("start").to("mock:target");
            }
        });
        // the rules are added when the context initializes; the startup then takes 30s
        context.build();
        clock.advance(STARTUP);
        context.start();
        return ChaosManager.get(context);
    }

    private static JsonObject rule(ChaosManager chaos, String id) {
        for (Object o : (JsonArray) chaos.report().toJson().get("rules")) {
            JsonObject rule = (JsonObject) o;
            if (id.equals(rule.getString("id"))) {
                return rule;
            }
        }
        throw new AssertionError("No rule " + id);
    }

    @Test
    void theUntilAndWithinOfAStartupRuleCountFromTheStart() throws Exception {
        ChaosManager chaos = startSlowly("endpoint=mock:target error until=1m expect=start:1 within=1m");

        JsonObject rule = rule(chaos, "r1");
        assertThat(rule.getLong("endsIn")).isEqualTo(60_000L);
        assertThat(((JsonObject) rule.get("expectation")).getLong("timeLeft")).isEqualTo(60_000L);
    }

    @Test
    void aRuleAddedOnceTheContextRunsKeepsItsClock() throws Exception {
        ChaosManager chaos = startSlowly("endpoint=nothing error");
        String id = chaos.addRule("endpoint=mock:target error until=1m");
        clock.advance(10_000);

        assertThat(rule(chaos, id).getLong("endsIn")).isEqualTo(50_000L);
        // a start of the context again does not start the clock of a rule again
        context.stop();
        context.start();
        assertThat(rule(chaos, id).getLong("endsIn")).isEqualTo(50_000L);
    }
}
