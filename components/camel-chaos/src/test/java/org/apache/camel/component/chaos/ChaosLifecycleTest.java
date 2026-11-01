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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.Route;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.chaos.ChaosRoutePolicyFactory.ChaosRoutePolicy;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.support.RoutePolicySupport;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * The route lifecycle faults: {@code stall} and {@code lifecycle action=suspend|stop|remove} act once on the route when
 * the rule is added, on the scheduled executor of chaos, and bring the route back after the duration; a route that is
 * not started is left alone, a rule removed early brings its route back at once, and a context stop cancels a pending
 * comeback.
 */
class ChaosLifecycleTest extends ChaosTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:a").routeId("a").to("mock:a");
                from("direct:b").routeId("b").to("mock:b");
                routeTemplate("tpl").templateParameter("name").from("direct:{{name}}").to("mock:tpl");
                templatedRoute("tpl").routeId("t").parameter("name", "t");
                // an exchange in flight that the test lets finish: a stop of the route waits for it
                from("direct:slow").routeId("slow").process(e -> release.await(20, TimeUnit.SECONDS));
                // a route whose removal (after it was stopped) or start fails once
                from("direct:c").routeId("c").routePolicy(new RoutePolicySupport() {
                    @Override
                    public void onRemove(Route route) {
                        if (failRemove.getAndSet(false)) {
                            throw new IllegalStateException("cannot remove now");
                        }
                    }

                    @Override
                    public void onStart(Route route) {
                        if (failStart.getAndSet(false)) {
                            throw new IllegalStateException("cannot start now");
                        }
                    }
                }).to("mock:c");
            }
        };
    }

    private final CountDownLatch release = new CountDownLatch(1);
    private final AtomicBoolean failRemove = new AtomicBoolean();
    private final AtomicBoolean failStart = new AtomicBoolean();

    @AfterEach
    void releaseTheSlowExchange() {
        release.countDown();
    }

    private ServiceStatus status(String routeId) {
        return context.getRouteController().getRouteStatus(routeId);
    }

    private JsonObject lifecycle(String id) {
        return (JsonObject) ruleReport(id).get("lifecycle");
    }

    /** The lifecycle of a version of a rule (the replaced versions follow the current rules in the report). */
    private JsonObject lifecycle(String id, int version) {
        for (Object o : (JsonArray) chaos().report().toJson().get("rules")) {
            JsonObject rule = (JsonObject) o;
            if (id.equals(rule.getString("id")) && rule.getInteger("version") == version) {
                return (JsonObject) rule.get("lifecycle");
            }
        }
        throw new AssertionError("No rule " + id + " v" + version);
    }

    /** Sends to the slow route without waiting, and waits until the exchange is in flight. */
    private void slowExchangeInFlight() {
        template.asyncSendBody("direct:slow", "x");
        await().atMost(10, TimeUnit.SECONDS).until(() -> context.getInflightRepository().size("slow") == 1);
    }

    private void awaitLifecycle(String id, String state) {
        await().atMost(20, TimeUnit.SECONDS).until(() -> state.equals(lifecycle(id).getString("state")));
    }

    @Test
    void stallSuspendsTheRouteAndResumesItAfterTheDuration() {
        String id = chaos().addRule("route=a stall duration=2s");
        await().atMost(10, TimeUnit.SECONDS).until(() -> status("a") == ServiceStatus.Suspended);
        assertThat(status("b")).isEqualTo(ServiceStatus.Started);
        awaitLifecycle(id, "back");
        assertThat(status("a")).isEqualTo(ServiceStatus.Started);

        JsonObject rule = ruleReport(id);
        assertThat(rule.getString("state")).isEqualTo("ended");
        assertThat(rule.getLong("matches")).isEqualTo(1);
        assertThat(rule.getLong("skipped")).isZero();
        assertThat(rule.containsKey("endsIn")).isFalse();
        JsonObject l = lifecycle(id);
        assertThat(l.getString("action")).isEqualTo("suspend");
        assertThat(l.getString("route")).isEqualTo("a");
        assertThat(l.getLong("duration")).isEqualTo(2000L);
        assertThat(l.getLong("backAt") - l.getLong("downAt")).isGreaterThanOrEqualTo(2000L);
        assertThat(((JsonObject) rule.get("fired")).getLong("lifecycle")).isEqualTo(1);
        assertThat(l.containsKey("reason")).isFalse();
        assertThat(chaos().report().toText())
                .contains("r1 ended: route=a stall duration=2s")
                .contains("suspended route a at +")
                .contains(", resumed at +");
    }

    @Test
    void theRouteTakesMessagesAgainAfterTheStall() throws Exception {
        String id = chaos().addRule("route=a stall duration=200ms");
        awaitLifecycle(id, "back");
        MockEndpoint mock = getMockEndpoint("mock:a");
        mock.expectedBodiesReceived("after");
        template.sendBody("direct:a", "after");
        mock.assertIsSatisfied();
    }

    @Test
    void stopStartsTheRouteAgain() {
        String id = chaos().addRule("route=a lifecycle action=stop duration=2s");
        await().atMost(10, TimeUnit.SECONDS).until(() -> status("a") == ServiceStatus.Stopped);
        awaitLifecycle(id, "back");
        assertThat(status("a")).isEqualTo(ServiceStatus.Started);
        assertThat(chaos().report().toText()).contains("stopped route a at +").contains(", started at +");
    }

    @Test
    void removeAddsTheSameDefinitionBack() throws Exception {
        ModelCamelContext model = (ModelCamelContext) context;
        RouteDefinition definition = model.getRouteDefinition("a");
        long completedBefore = routeCount("a", "completed");

        String id = chaos().addRule("route=a lifecycle action=remove duration=2s");
        await().atMost(10, TimeUnit.SECONDS).until(() -> context.getRoute("a") == null);
        assertThat(model.getRouteDefinition("a")).isNull();
        awaitLifecycle(id, "back");

        Route route = context.getRoute("a");
        assertThat(route).isNotNull();
        assertThat(status("a")).isEqualTo(ServiceStatus.Started);
        assertThat(model.getRouteDefinition("a")).isSameAs(definition);
        // the route added back has one chaos route policy, so it counts each exchange once
        assertThat(route.getRoutePolicyList()).filteredOn(p -> p instanceof ChaosRoutePolicy).hasSize(1);
        MockEndpoint mock = getMockEndpoint("mock:a");
        mock.expectedBodiesReceived("again");
        template.sendBody("direct:a", "again");
        mock.assertIsSatisfied();
        assertThat(routeCount("a", "completed")).isEqualTo(completedBefore + 1);
        assertThat(chaos().report().toText()).contains("removed route a at +").contains(", added back at +");
    }

    @Test
    void aRouteThatIsNotStartedIsLeftAlone() throws Exception {
        context.getRouteController().stopRoute("a");
        String id = chaos().addRule("route=a stall duration=200ms");
        awaitLifecycle(id, "skipped");
        assertThat(status("a")).isEqualTo(ServiceStatus.Stopped);
        JsonObject rule = ruleReport(id);
        assertThat(rule.getString("state")).isEqualTo("ended");
        assertThat(rule.getLong("matches")).isEqualTo(1);
        assertThat(rule.getLong("skipped")).isEqualTo(1);
        assertThat(lifecycle(id).getString("reason")).isEqualTo("route a was stopped");
        assertThat(lifecycle(id).containsKey("downAt")).isFalse();
        assertThat(chaos().report().toText()).contains("did nothing: route a was stopped");
    }

    @Test
    void aRouteSomeoneElseChangedMeanwhileIsLeftAsItIs() throws Exception {
        String id = chaos().addRule("route=a stall duration=10m");
        await().atMost(10, TimeUnit.SECONDS).until(() -> "down".equals(lifecycle(id).getString("state")));
        // someone stops the suspended route: chaos does not resume (and so start) it
        context.getRouteController().stopRoute("a");
        // the comeback, now: removing the rule brings the route back at once
        chaos().removeRule(id);
        awaitLifecycle(id, "left");
        assertThat(status("a")).isEqualTo(ServiceStatus.Stopped);
        assertThat(lifecycle(id).getString("reason")).isEqualTo("route a is stopped now, so chaos left it");
        assertThat(chaos().report().toText()).contains("not resumed at +").contains("route a is stopped now");
    }

    @Test
    void aRouteLoadedAgainMeanwhileIsNotReplaced() throws Exception {
        String id = chaos().addRule("route=a lifecycle action=remove duration=10m");
        await().atMost(10, TimeUnit.SECONDS).until(() -> "down".equals(lifecycle(id).getString("state")));
        // as a reload in dev mode does: a new route with the same id while the old one is removed
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:a").routeId("a").to("mock:reloaded");
            }
        });
        // the comeback, now: removing the rule brings the route back at once
        chaos().removeRule(id);
        awaitLifecycle(id, "left");
        assertThat(lifecycle(id).getString("reason")).isEqualTo("a route a was added meanwhile, so chaos left it");
        MockEndpoint reloaded = getMockEndpoint("mock:reloaded");
        reloaded.expectedMessageCount(1);
        template.sendBody("direct:a", "x");
        reloaded.assertIsSatisfied();
    }

    @Test
    void aRouteReplacedMeanwhileIsNotStarted() throws Exception {
        String id = chaos().addRule("route=a lifecycle action=stop duration=10m");
        await().atMost(10, TimeUnit.SECONDS).until(() -> "down".equals(lifecycle(id).getString("state")));
        context.removeRoute("a");
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:a").routeId("a").autoStartup(false).to("mock:replaced");
            }
        });
        // the comeback, now: removing the rule brings the route back at once
        chaos().removeRule(id);
        awaitLifecycle(id, "left");
        assertThat(lifecycle(id).getString("reason")).isEqualTo("route a was replaced meanwhile, so chaos left it");
        assertThat(status("a")).isEqualTo(ServiceStatus.Stopped);
    }

    @Test
    void removingTheRuleWhileItActsBringsTheRouteBackWhenTheActionIsDone() {
        slowExchangeInFlight();
        String id = chaos().addRule("route=slow lifecycle action=stop duration=10m");
        // the stop waits for the exchange in flight
        awaitLifecycle(id, "acting");
        assertThat(chaos().removeRule(id)).isTrue();
        release.countDown();
        awaitLifecycle(id, "back");
        assertThat(lifecycle(id).getBoolean("endedEarly")).isTrue();
        assertThat(status("slow")).isEqualTo(ServiceStatus.Started);
    }

    @Test
    void aRuleChangedWhileItActsActsAfterTheOldOneBroughtTheRouteBack() {
        slowExchangeInFlight();
        String id = chaos().addRule("route=slow lifecycle action=stop duration=10m");
        awaitLifecycle(id, "acting");
        chaos().changeRule(id, "route=slow stall duration=10m");
        release.countDown();
        // version 1 brings the route back, then version 2 suspends it (it is not skipped as "stopped")
        await().atMost(20, TimeUnit.SECONDS).until(() -> "down".equals(lifecycle(id, 2).getString("state")));
        assertThat(lifecycle(id, 1).getString("state")).isEqualTo("back");
        assertThat(status("slow")).isEqualTo(ServiceStatus.Suspended);
    }

    @Test
    void lifecycleRulesActAgainAfterTheContextIsStartedAgain() {
        String first = chaos().addRule("route=a stall duration=200ms");
        awaitLifecycle(first, "back");
        context.stop();
        assertThat(chaos().lifecycle().hasExecutor()).isFalse();
        context.start();
        String second = chaos().addRule("route=a stall duration=200ms");
        awaitLifecycle(second, "back");
        // the first rule did not act again
        assertThat(ruleReport(first).getLong("matches")).isEqualTo(1);
        assertThat(status("a")).isEqualTo(ServiceStatus.Started);
    }

    @Test
    void aRemoveThatFailsAfterTheStopStartsTheRouteAfterTheDuration() {
        failRemove.set(true);
        String id = chaos().addRule("route=c lifecycle action=remove duration=300ms");
        awaitLifecycle(id, "back");
        assertThat(status("c")).isEqualTo(ServiceStatus.Started);
        assertThat(lifecycle(id).getString("reason"))
                .startsWith("could not remove route c: ").contains("IllegalStateException: cannot remove now")
                .endsWith("; route c was left stopped, so chaos starts it after the duration");
        assertThat(lifecycle(id).getLong("backAt") - lifecycle(id).getLong("downAt")).isGreaterThanOrEqualTo(300L);
    }

    @Test
    void aComebackThatFailsIsReported() {
        String id = chaos().addRule("route=c lifecycle action=stop duration=10m");
        awaitLifecycle(id, "down");
        failStart.set(true);
        chaos().removeRule(id);
        awaitLifecycle(id, "failed");
        assertThat(lifecycle(id).getString("reason"))
                .startsWith("could not start route c: ").contains("IllegalStateException: cannot start now");
        assertThat(status("c")).isNotEqualTo(ServiceStatus.Started);
        assertThat(chaos().report().toText()).contains("stopped route c at +").contains("could not start route c");
    }

    @Test
    void aRouteFromATemplateIsNotRemovedButCanBeStopped() {
        assertThatThrownBy(() -> chaos().addRule("route=t lifecycle action=remove duration=1s"))
                .hasMessage("Invalid chaos rule [route=t lifecycle action=remove duration=1s]: route t is created from a"
                            + " route template or a kamelet, whose local beans go with the route, so chaos could not add"
                            + " it back; use action=stop or stall");
        String id = chaos().addRule("route=t lifecycle action=stop duration=200ms");
        awaitLifecycle(id, "back");
        assertThat(status("t")).isEqualTo(ServiceStatus.Started);
    }

    @Test
    void removingTheRuleBringsTheRouteBackNow() {
        String id = chaos().addRule("route=a lifecycle action=stop duration=10m");
        await().atMost(10, TimeUnit.SECONDS).until(() -> "down".equals(lifecycle(id).getString("state")));
        assertThat(status("a")).isEqualTo(ServiceStatus.Stopped);
        assertThat(lifecycle(id).getLong("backIn")).isPositive();

        assertThat(chaos().removeRule(id)).isTrue();
        awaitLifecycle(id, "back");
        assertThat(status("a")).isEqualTo(ServiceStatus.Started);
        JsonObject rule = ruleReport(id);
        assertThat(rule.getString("state")).isEqualTo("removed");
        assertThat(lifecycle(id).getBoolean("endedEarly")).isTrue();
        assertThat(lifecycle(id).getLong("backAt")).isLessThan(600_000L);
        assertThat(chaos().lifecycle().activeCount()).isZero();
    }

    @Test
    void clearAndChangeBringTheRoutesBack() {
        String a = chaos().addRule("route=a stall duration=10m");
        String b = chaos().addRule("route=b lifecycle action=stop duration=10m");
        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> status("a") == ServiceStatus.Suspended && status("b") == ServiceStatus.Stopped);

        // a change replaces the rule: the old fault ends (route a resumes), and the new rule acts once
        chaos().changeRule(a, "route=a lifecycle action=stop duration=10m");
        await().atMost(10, TimeUnit.SECONDS).until(() -> status("a") == ServiceStatus.Stopped
                && "down".equals(lifecycle(a).getString("state")));
        assertThat(chaos().report().toJson().toJson()).contains("\"version\":1");

        assertThat(chaos().clearRules()).isEqualTo(2);
        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> status("a") == ServiceStatus.Started && status("b") == ServiceStatus.Started);
        await().atMost(10, TimeUnit.SECONDS).until(() -> chaos().lifecycle().activeCount() == 0);
        assertThat(chaos().report().toText()).contains("back early as the rule was removed");
        assertThat(b).isEqualTo("r2");
    }

    @Test
    void aContextStopCancelsThePendingComeback() {
        String id = chaos().addRule("route=a stall duration=10m");
        await().atMost(10, TimeUnit.SECONDS).until(() -> "down".equals(lifecycle(id).getString("state")));
        assertThat(chaos().lifecycle().hasExecutor()).isTrue();

        ChaosManager chaos = chaos();
        context.stop();
        assertThat(chaos.lifecycle().hasExecutor()).isFalse();
        assertThat(chaos.lifecycle().activeCount()).isZero();
        JsonObject rule = null;
        for (Object o : (Iterable<?>) chaos.report().toJson().get("rules")) {
            rule = (JsonObject) o;
        }
        JsonObject l = (JsonObject) rule.get("lifecycle");
        assertThat(l.getString("state")).isEqualTo("cancelled");
        assertThat(l.getString("reason")).isEqualTo("the context stopped, so route a was not resumed");
        // no thread of chaos is left behind
        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> Thread.getAllStackTraces().keySet().stream()
                        .noneMatch(t -> t.isAlive() && t.getName().endsWith(" - Chaos")));
    }

    @Test
    void anExpectationOfAStalledRoute() throws Exception {
        String id = chaos().addRule("route=a stall duration=200ms expect=a:2 within=1m");
        awaitLifecycle(id, "back");
        template.sendBody("direct:a", "1");
        template.sendBody("direct:a", "2");
        JsonObject expectation = (JsonObject) ruleReport(id).get("expectation");
        assertThat(expectation.getString("outcome")).isEqualTo("met");
        assertThat(chaos().report().getVerdict()).isEqualTo(ChaosReport.PASS);
        assertThat(chaos().report().isFinal()).isTrue();
    }

    @Test
    void theRouteIsCheckedWhenTheRuleIsAdded() {
        assertThatThrownBy(() -> chaos().addRule("route=nope stall duration=1s"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid chaos rule [route=nope stall duration=1s]: there is no route nope (the fault stall"
                            + " acts on a route that exists when the rule is added)");
        String id = chaos().addRule("route=a stall duration=10m");
        assertThatThrownBy(() -> chaos().changeRule(id, "route=nope lifecycle action=stop duration=1s"))
                .hasMessageContaining("there is no route nope (the fault lifecycle acts");
        assertThatThrownBy(() -> chaos().addRule("route=a stall duration=11m"))
                .hasMessageContaining("duration=11m is longer than camel.chaos.maxDuration (10m0s)");
        assertThatThrownBy(() -> chaos().addRule("route=a stall duration=1m rate=0.5"))
                .hasMessageContaining("rate= does not apply to the fault stall");
        // a rejected rule takes no id
        assertThat(chaos().addRule("route=b stall duration=10m")).isEqualTo("r2");
    }
}
