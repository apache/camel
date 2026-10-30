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

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.chaos.ChaosRoutePolicyFactory.ChaosRoutePolicy;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.SendHook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * When chaos is switched on: only with profile dev or camel.chaos.enabled=true, never with profile prod, and with
 * nothing installed otherwise.
 */
class ChaosActivationTest {

    private CamelContext context;

    @AfterEach
    void stop() {
        if (context != null) {
            context.stop();
        }
    }

    private CamelContext start(String profile, Map<String, String> properties) throws Exception {
        context = new DefaultCamelContext();
        context.getCamelContextExtension().setProfile(profile);
        properties.forEach(context.getPropertiesComponent()::addInitialProperty);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("start").to("mock:target");
            }
        });
        context.start();
        return context;
    }

    private static void assertNothingInstalled(CamelContext context) {
        assertThat(ChaosManager.get(context)).isNull();
        assertThat(context.getCamelContextExtension().getSendHook()).isNull();
        assertThat(context.getCamelContextExtension().getStepHook()).isNull();
        assertThat(context.getRoutePolicyFactories()).noneMatch(f -> f instanceof ChaosRoutePolicyFactory);
        assertThat(context.getRoute("start").getRoutePolicyList()).noneMatch(p -> p instanceof ChaosRoutePolicy);
        assertThat(context.getManagementStrategy().getEventNotifiers()).noneMatch(n -> n instanceof ChaosEventNotifier);
    }

    private static void assertInstalled(CamelContext context) {
        assertThat(ChaosManager.get(context)).isNotNull();
        assertThat(context.getCamelContextExtension().getSendHook()).isInstanceOf(ChaosSendHook.class);
        assertThat(context.getCamelContextExtension().getStepHook()).isInstanceOf(ChaosStepHook.class);
        assertThat(context.getRoute("start").getRoutePolicyList()).anyMatch(p -> p instanceof ChaosRoutePolicy);
        assertThat(context.getManagementStrategy().getEventNotifiers()).anyMatch(n -> n instanceof ChaosEventNotifier);
    }

    @Test
    void offWithoutProfile() throws Exception {
        assertNothingInstalled(start(null, Map.of()));
    }

    @Test
    void offWithoutProfileAlsoWithRules() throws Exception {
        // camel.chaos.rules left in the properties does nothing without camel.chaos.enabled=true
        assertNothingInstalled(start(null, Map.of("camel.chaos.rules", "endpoint=mock:target error")));
        assertThat(context.createProducerTemplate().send("direct:start", e -> e.getMessage().setBody("x"))
                .getException()).isNull();
    }

    @Test
    void offWithProfileTest() throws Exception {
        assertNothingInstalled(start("test", Map.of()));
    }

    @Test
    void onWithProfileDev() throws Exception {
        assertInstalled(start("dev", Map.of()));
    }

    @Test
    void onWithEnabled() throws Exception {
        assertInstalled(start(null, Map.of("camel.chaos.enabled", "true")));
    }

    @Test
    void enabledFalseWinsOverProfileDev() throws Exception {
        assertNothingInstalled(start("dev", Map.of("camel.chaos.enabled", "false")));
    }

    @Test
    void neverWithProfileProd() throws Exception {
        assertNothingInstalled(start("prod", Map.of("camel.chaos.enabled", "true",
                "camel.chaos.rules", "endpoint=mock:target error")));
        assertThat(context.createProducerTemplate().send("direct:start", e -> e.getMessage().setBody("x"))
                .getException()).isNull();
    }

    @Test
    void neverWithProfileProdInAnyCase() throws Exception {
        assertNothingInstalled(start("PROD", Map.of("camel.chaos.enabled", "true")));
    }

    @Test
    void anInvalidOptionDoesNotStopAnApplicationInProd() throws Exception {
        assertNothingInstalled(start("prod", Map.of("camel.chaos.enabled", "yes", "camel.chaos.maxDuration", "soon")));
    }

    @Test
    void endedRulesMakeRoomForNewOnes() throws Exception {
        start(null, Map.of("camel.chaos.enabled", "true"));
        ChaosManager chaos = ChaosManager.get(context);
        ProducerTemplate template = context.createProducerTemplate();
        for (int i = 0; i < ChaosManager.MAX_RULES; i++) {
            chaos.addRule("endpoint=mock:target drop until=1");
        }
        // the first rule that fires decides a send, so each send ends one rule
        for (int i = 0; i < ChaosManager.MAX_RULES; i++) {
            template.sendBody("direct:start", "x");
        }
        String id = chaos.addRule("endpoint=mock:target error");
        assertThat(chaos.report().toText()).contains("r1 ended", id + " active");
        // a rule that has not ended still counts
        assertThatThrownBy(() -> {
            for (int i = 0; i < ChaosManager.MAX_RULES; i++) {
                chaos.addRule("endpoint=other error");
            }
        }).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rulesAtStartup() throws Exception {
        start("dev", Map.of("camel.chaos.rules",
                "endpoint=mock:target error message=\"a;b\"; step=nothing delay duration=1s; route=start corrupt mode=headers",
                "camel.chaos.seed", "7"));
        ChaosManager chaos = ChaosManager.get(context);
        assertThat(chaos.getSeed()).isEqualTo(7);
        Exchange out = context.createProducerTemplate().send("direct:start", e -> e.getMessage().setBody("x"));
        assertThat(out.getException()).isInstanceOf(ChaosException.class).hasMessage("a;b");
        assertThat(chaos.report().toText()).contains("r1 active").contains("r2 active").contains("r3 active");
    }

    @Test
    void anExpectationOfAnUnknownRouteIsWarnedFor() throws Exception {
        start(null, Map.of("camel.chaos.enabled", "true",
                "camel.chaos.rules", "endpoint=x error expect=strat:1; endpoint=y error expect=start:1"));
        ChaosManager chaos = ChaosManager.get(context);
        // once, for r1, when the context started
        assertThat(chaos.unknownRouteWarnings.get()).isEqualTo(1);
        assertThat(chaos.contextStarted()).containsExactly("r1");
        // when a rule is added or changed while the context runs
        String id = chaos.addRule("endpoint=z error expect=ordrs:1");
        assertThat(chaos.unknownRouteWarnings.get()).isEqualTo(3);
        chaos.changeRule(id, "endpoint=z error expect=start:1");
        assertThat(chaos.unknownRouteWarnings.get()).isEqualTo(3);
        chaos.changeRule(id, "endpoint=z error expect=strt:1");
        assertThat(chaos.unknownRouteWarnings.get()).isEqualTo(4);
    }

    @Test
    void anInvalidRuleAtStartupStopsTheStart() {
        assertThatThrownBy(() -> start(null, Map.of("camel.chaos.enabled", "true",
                "camel.chaos.rules", "endpoint=mock:target error; step=s1 drop")))
                .hasStackTraceContaining("camel-chaos: Invalid chaos rule [step=s1 drop]")
                .hasStackTraceContaining("is not offered");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "camel.chaos.enabled|yes|camel.chaos.enabled=yes is not true or false",
            "camel.chaos.seed|x|camel.chaos.seed=x is not a whole number",
            "camel.chaos.maxDuration|0|camel.chaos.maxDuration=0 must be longer than 0",
            "camel.chaos.maxDuration|soon|camel.chaos.maxDuration=soon is not a time",
            "camel.chaos.reportOnShutdown|maybe|camel.chaos.reportOnShutdown=maybe is not true or false" })
    void invalidOptionsStopTheStart(String key, String value, String reason) {
        Map<String, String> properties = "camel.chaos.enabled".equals(key)
                ? Map.of(key, value) : Map.of("camel.chaos.enabled", "true", key, value);
        assertThatThrownBy(() -> start(null, properties)).hasStackTraceContaining(reason);
    }

    @Test
    void maxDurationBoundsTheRules() throws Exception {
        start(null, Map.of("camel.chaos.enabled", "true", "camel.chaos.maxDuration", "30s"));
        assertThatThrownBy(() -> ChaosManager.get(context).addRule("endpoint=x error until=1m"))
                .hasMessageContaining("is longer than camel.chaos.maxDuration (30s)");
    }

    @Test
    void aSeedIsPickedWithoutOne() throws Exception {
        start(null, Map.of("camel.chaos.enabled", "true"));
        ChaosReport report = ChaosManager.get(context).report();
        assertThat(report.toText()).contains("seed: " + report.getSeed() + " (picked at startup)");
    }

    @Test
    void atMostMaxRules() throws Exception {
        start(null, Map.of("camel.chaos.enabled", "true"));
        ChaosManager chaos = ChaosManager.get(context);
        for (int i = 0; i < ChaosManager.MAX_RULES; i++) {
            chaos.addRule("endpoint=x" + i + " error");
        }
        assertThatThrownBy(() -> chaos.addRule("endpoint=y error")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("There are 100 chaos rules already");
        assertThat(chaos.clearRules()).isEqualTo(ChaosManager.MAX_RULES);
        chaos.addRule("endpoint=y error");
    }

    @Test
    void theHooksStayWhenTheContextIsStartedAgain() throws Exception {
        start(null, Map.of("camel.chaos.enabled", "true"));
        ChaosManager chaos = ChaosManager.get(context);
        chaos.addRule("endpoint=mock:target error");
        context.stop();
        context.start();

        assertThat(ChaosManager.get(context)).isSameAs(chaos);
        assertThat(context.getLifecycleStrategies())
                .filteredOn(s -> s instanceof ChaosContextServicePlugin.Activation).hasSize(1);
        ProducerTemplate template = context.createProducerTemplate();
        assertThat(template.send("direct:start", e -> e.getMessage().setBody("x")).getException())
                .isInstanceOf(ChaosException.class);
        long notifiers = context.getManagementStrategy().getEventNotifiers().stream()
                .filter(n -> n instanceof ChaosEventNotifier).count();
        assertThat(notifiers).isEqualTo(1);
        // the restarted route has a chaos route policy once
        assertThat(context.getRoute("start").getRoutePolicyList()).filteredOn(p -> p instanceof ChaosRoutePolicy)
                .hasSize(1);
    }

    @Test
    void anotherSendHookIsKept() throws Exception {
        context = new DefaultCamelContext();
        SendHook mine = new SendHook() {
            @Override
            public long beforeSend(Exchange exchange, Endpoint endpoint) {
                return PROCEED;
            }

            @Override
            public void afterSend(Exchange exchange, Endpoint endpoint) {
                // nothing
            }
        };
        context.getCamelContextExtension().setSendHook(mine);
        context.getPropertiesComponent().addInitialProperty("camel.chaos.enabled", "true");
        context.start();
        assertThat(context.getCamelContextExtension().getSendHook()).isSameAs(mine);
        assertThat(context.getCamelContextExtension().getStepHook()).isInstanceOf(ChaosStepHook.class);
    }
}
