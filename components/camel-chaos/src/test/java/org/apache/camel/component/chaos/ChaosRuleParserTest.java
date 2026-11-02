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

import java.io.IOException;
import java.net.ConnectException;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.camel.component.chaos.ChaosRule.CorruptMode;
import org.apache.camel.component.chaos.ChaosRule.Fault;
import org.apache.camel.component.chaos.ChaosRule.Hook;
import org.apache.camel.component.chaos.ChaosRule.Target;
import org.apache.camel.impl.engine.DefaultClassResolver;
import org.apache.camel.spi.ClassResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChaosRuleParserTest {

    private static final long MAX = TimeUnit.MINUTES.toMillis(10);
    private final ClassResolver resolver = new DefaultClassResolver();

    private ChaosRule.Builder parse(String rule) {
        return ChaosRuleParser.parse(rule, resolver, MAX);
    }

    private void rejected(String rule, String reason) {
        assertThatThrownBy(() -> parse(rule))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(reason);
    }

    @Test
    void errorWithExceptionMessageRateAndUntil() {
        ChaosRule.Builder b = parse(
                "endpoint=kafka:orders* error exception=" + ConnectException.class.getName()
                                    + " message=\"broker down\" rate=0.3 until=1m");
        assertThat(b.target).isEqualTo(Target.ENDPOINT);
        assertThat(b.pattern).isEqualTo("kafka:orders*");
        assertThat(b.fault).isEqualTo(Fault.ERROR);
        assertThat(b.exceptionType).isEqualTo(ConnectException.class);
        assertThat(b.exceptionTakesMessage).isTrue();
        assertThat(b.message).isEqualTo("broker down");
        assertThat(b.probability).isEqualTo(0.3d);
        assertThat(b.untilDuration).isEqualTo(60_000L);
        assertThat(b.untilCount).isZero();
        // an error fires at both hooks of an endpoint
        assertThat(b.hooks).containsExactlyInAnyOrder(Hook.SEND, Hook.POLL);
    }

    @Test
    void errorWithoutParametersIsAChaosException() {
        ChaosRule.Builder b = parse("component=kafka error");
        assertThat(b.exceptionType).isNull();
        assertThat(b.hooks).containsExactlyInAnyOrder(Hook.SEND, Hook.POLL);
    }

    @Test
    void httpStatusFiresAtSendsOnly() {
        ChaosRule.Builder b = parse("endpoint=https:inventory* error status=503 header.Retry-After=5 rate=every:4 until=50");
        assertThat(b.status).isEqualTo(503);
        assertThat(b.responseHeaders).containsExactly(Map.entry("Retry-After", "5"));
        assertThat(b.every).isEqualTo(4);
        assertThat(b.untilCount).isEqualTo(50);
        assertThat(b.hooks).containsExactly(Hook.SEND);
        // the scheme is open: decided at the send
        assertThat(parse("endpoint=*inventory* error status=503 body=\"no stock\"").responseBody).isEqualTo("no stock");
    }

    @Test
    void delayDropCorrupt() {
        ChaosRule.Builder delay = parse("endpoint=kafka:orders* delay duration=2s rate=0.2 until=5m");
        assertThat(delay.duration).isEqualTo(2000L);
        ChaosRule.Builder drop = parse("endpoint=file:inbox* drop at=poll rate=0.5 until=2m");
        assertThat(drop.hooks).containsExactly(Hook.POLL);
        assertThat(drop.at).isEqualTo(Hook.POLL);
        ChaosRule.Builder corrupt = parse("route=orders corrupt mode=truncate rate=0.1 until=20");
        assertThat(corrupt.mode).isEqualTo(CorruptMode.TRUNCATE);
        assertThat(corrupt.hooks).containsExactly(Hook.ENTRY);
        // corrupt is not offered at polls, so an endpoint rule fires at sends only
        assertThat(parse("endpoint=file:inbox* corrupt mode=headers").hooks).containsExactly(Hook.SEND);
        assertThat(parse("step=enrich* delay duration=100ms").hooks).containsExactly(Hook.STEP);
        assertThat(parse("step=s1 error exception=" + IOException.class.getName()).exceptionType).isEqualTo(IOException.class);
    }

    @Test
    void lifecycle() {
        ChaosRule.Builder stall = parse("route=poller stall duration=20s");
        assertThat(stall.fault).isEqualTo(Fault.LIFECYCLE);
        assertThat(stall.stall).isTrue();
        assertThat(stall.action).isEqualTo(ChaosRule.Action.SUSPEND);
        assertThat(stall.target).isEqualTo(Target.ROUTE);
        assertThat(stall.pattern).isEqualTo("poller");
        assertThat(stall.duration).isEqualTo(20_000L);
        assertThat(stall.hooks).isEmpty();
        for (ChaosRule.Action action : ChaosRule.Action.values()) {
            ChaosRule.Builder b = parse("route=orders lifecycle action=" + action.key + " duration=10s");
            assertThat(b.action).isEqualTo(action);
            assertThat(b.stall).isFalse();
            assertThat(b.duration).isEqualTo(10_000L);
        }
        // the longest duration is camel.chaos.maxDuration
        assertThat(parse("route=orders stall duration=10m").duration).isEqualTo(600_000L);
        // an expectation of the route that is stalled
        ChaosRule.Builder expect = parse("route=orders stall duration=1m expect=orders:10 within=5m");
        assertThat(expect.expectRoute).isEqualTo("orders");
        assertThat(expect.within).isEqualTo(300_000L);
        // action= belongs to lifecycle
        assertThatThrownBy(() -> parse("endpoint=x delay duration=1s action=stop"))
                .hasMessageEndingWith("action= is a parameter of the fault lifecycle (route=<id> lifecycle"
                                      + " action=suspend|stop|remove duration=<time>), not of the fault delay");
    }

    @Test
    void theRouteOfALifecycleRuleIsChecked() {
        ChaosRuleParser.checkRoute("route=a stall duration=1s", true, ChaosRule.Action.SUSPEND, "a", "a"::equals,
                id -> "never asked for suspend");
        assertThatThrownBy(() -> ChaosRuleParser.checkRoute("route=b stall duration=1s", true, ChaosRule.Action.SUSPEND,
                "b", "a"::equals, id -> null))
                .hasMessage("Invalid chaos rule [route=b stall duration=1s]: there is no route b (the fault stall acts on"
                            + " a route that exists when the rule is added)");
        assertThatThrownBy(() -> ChaosRuleParser.checkRoute("route=a lifecycle action=remove duration=1s", false,
                ChaosRule.Action.REMOVE, "a", "a"::equals, id -> "route a has no route definition, so chaos could not add"
                                                                 + " it back"))
                .hasMessage("Invalid chaos rule [route=a lifecycle action=remove duration=1s]: route a has no route"
                            + " definition, so chaos could not add it back");
    }

    @Test
    void expectation() {
        ChaosRule.Builder b = parse("endpoint=kafka:orders* error rate=0.3 until=1m expect=orders:100 within=2m");
        assertThat(b.expectRoute).isEqualTo("orders");
        assertThat(b.expectCount).isEqualTo(100);
        assertThat(b.within).isEqualTo(120_000L);
        // a route id with a colon: the count is after the last one
        assertThat(parse("endpoint=x error expect=a:b:7").expectRoute).isEqualTo("a:b");
    }

    @Test
    void quotedValuesAndEscapes() {
        assertThat(parse("endpoint=x error message='it is \\'down\\''").message).isEqualTo("it is 'down'");
        assertThat(parse("endpoint=x error message=\"a \\\\ b ; c\"").message).isEqualTo("a \\ b ; c");
        assertThat(parse("  endpoint=x\terror  ").fault).isEqualTo(Fault.ERROR);
    }

    @Test
    void splitRulesIsQuoteAware() {
        assertThat(ChaosRuleParser.splitRules("route=a corrupt mode=headers; endpoint=b error message=\"x;y\" ;;"))
                .containsExactly("route=a corrupt mode=headers", "endpoint=b error message=\"x;y\"");
        assertThat(ChaosRuleParser.splitRules(null)).isEmpty();
        assertThat(ChaosRuleParser.splitRules(" ; ")).isEmpty();
        // an unterminated quote keeps the rest in one rule, which parse() then rejects
        List<String> rules = ChaosRuleParser.splitRules("endpoint=a error message=\"x; endpoint=b drop");
        assertThat(rules).hasSize(1);
        assertThatThrownBy(() -> parse(rules.get(0))).hasMessageContaining("is not closed");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "''|is empty",
            "error|needs a target",
            "rout=orders error|a rule needs a target: route=<id>, endpoint=<uri>, component=<name> or step=<id>"
                                    + " (rout= is not a target)",
            "endpoint=x error when=x|when= (a condition on the message) is not supported yet",
            "endpoint=x error action=stop|action= is a parameter of the fault lifecycle (route=<id> lifecycle action=",
            "endpoint=netty-http:x error status=503|status= is not supported yet for netty-http",
            "component=netty-http error status=503|it is supported for http, https, undertow, vertx-http",
            "route=orders error|route= does not reach the sends and steps of the route: target them with endpoint=,"
                                                                                                           + " component= or step=",
            "step=s1 drop|drop is a fault of the sends and polls: target them with endpoint= or component=",
            "Endpoint=x error|(Endpoint= is not a target)",
            "endpoint=x|needs a fault",
            "endpoint=x error drop|has one fault",
            "endpoint=x step=y error|has one target",
            "endpoint=x route=y error|has one target",
            "endpoint=x explode|unknown fault explode",
            "route=orders stall|the fault stall needs duration=<time>, how long the route stays suspended",
            "route=orders lifecycle action=stop|the fault lifecycle needs duration=<time>, how long the route stays stopped",
            "route=orders lifecycle duration=10s|the fault lifecycle needs action=suspend, action=stop or action=remove",
            "route=orders lifecycle action=pause duration=10s|unknown action=pause (the actions are suspend, stop and remove)",
            "route=orders stall action=stop duration=10s|stall is short for lifecycle action=suspend, so it takes no action=",
            "route=orders stall duration=11m|duration=11m is longer than camel.chaos.maxDuration (10m0s)",
            "route=orders stall duration=0|duration=0 must be longer than 0",
            "route=orders stall duration=soon|duration=soon is not a time",
            "route=orders stall duration=1m rate=0.5|rate= does not apply to the fault stall: it acts once on the route"
                                                                            + " when the rule is added, and brings it back after its duration",
            "route=orders lifecycle action=stop duration=1m until=5|until= does not apply to the fault lifecycle",
            "route=orders stall duration=1m until=1m|until= does not apply to the fault stall",
            "route=orders stall duration=1m at=entry|at= does not apply to the fault stall",
            "route=orders stall duration=1m mode=headers|unknown parameter mode for the fault stall (its parameters are"
                                                                                             + " duration, expect, within)",
            "route=orders lifecycle action=stop duration=1m mode=x|unknown parameter mode for the fault lifecycle (its"
                                                                                                                             + " parameters are action, duration, expect, within)",
            "route=orders stall lifecycle duration=1m|a rule has one fault, found stall and lifecycle",
            "endpoint=kafka:x stall duration=1m|the fault stall acts on a route, so it takes only a route=<id> target, not"
                                                                                                        + " endpoint=",
            "component=kafka lifecycle action=stop duration=1m|takes only a route=<id> target, not component=",
            "step=s1 stall duration=1m|takes only a route=<id> target, not step=",
            "route=order* stall duration=1m|the fault stall acts on one route, so route= needs the id of the route, not a"
                                                                                   + " pattern",
            "route=orders stall error duration=1m|has one fault",
            "rate=1 duration=1m stall|needs a target",
            "route=orders error|the fault error is not offered at any hook of the target route=",
            "route=orders delay duration=1s|the hooks of route= are: at=entry offers corrupt",
            "route=orders drop|is not offered",
            "step=s1 drop|is not offered",
            "step=s1 corrupt mode=headers|is not offered",
            "endpoint=x corrupt mode=headers at=poll|the fault corrupt is not offered at=poll",
            "endpoint=x error at=entry|at=entry is not a hook of the target endpoint=",
            "route=x corrupt mode=headers at=send|at=send is not a hook of the target route=",
            "endpoint=x error at=nowhere|unknown at=nowhere",
            "endpoint=x error foo=1|unknown parameter foo for the fault error",
            "endpoint=x delay|needs duration=",
            "endpoint=x delay duration=0|must be longer than 0",
            "endpoint=x delay duration=abc|is not a time",
            "endpoint=x delay duration=-5s|is not a time",
            "endpoint=x delay duration=11m|is longer than camel.chaos.maxDuration",
            "endpoint=x drop duration=1s|unknown parameter duration for the fault drop",
            "endpoint=x corrupt|needs mode=",
            "endpoint=x corrupt mode=bits|unknown mode=bits",
            "endpoint=x error exception=does.not.Exist|is not found",
            "endpoint=x error exception=java.lang.String|is not a Throwable",
            "endpoint=x error exception=java.lang.Exception;x|is not found",
            "endpoint=https:x error status=99|is not an HTTP failure status",
            "endpoint=https:x error status=5030|needs a whole number of at most 3 digits",
            "endpoint=https:x error status=200|is not an HTTP failure status",
            "endpoint=https:x error status=600|is not an HTTP failure status",
            "endpoint=https:x error status=abc|needs a whole number",
            "endpoint=https:x error status=503 message=x|cannot be used with message=",
            "endpoint=kafka:x error status=503|not for kafka",
            "component=kafka error status=503|not for kafka",
            "step=s1 error status=503|status= is the failure of a send",
            "step=s1 error status=503|fires at the sends of an endpoint= or component= target (not step=)",
            "route=x error status=503|(not route=)",
            "step=s1 corrupt mode=headers|corrupt is a fault of the sends (endpoint=, component=) and of the entry of a"
                                                     + " route (route=)",
            "endpoint=https:x error status=503 at=poll|status= is the failure of a send",
            "endpoint=https:x error header.Retry-After=5|need status=<code>",
            "endpoint=https:x error body=x|need status=<code>",
            "endpoint=https:x error status=503 header.=5|needs a header name",
            "endpoint=x error rate=0|is not a probability above 0",
            "endpoint=x error rate=1.5|is not a probability above 0",
            "endpoint=x error rate=-0.1|is neither a probability",
            "endpoint=x error rate=1e-3|is neither a probability",
            "endpoint=x error rate=NaN|is neither a probability",
            "endpoint=x error rate=0.1.2|is neither a probability",
            "endpoint=x error rate=.|is neither a probability",
            "endpoint=x error rate=every:0|needs a number of 1 or more",
            "endpoint=x error rate=every:x|needs a whole number",
            "endpoint=x error rate=every:|rate=every: needs a whole number of at most 9 digits after every:",
            "endpoint=x error expect=ord*:5|expect=ord*:5 needs the id of one route, not a pattern",
            "endpoint=x error expect=orders:1234567890123456789|expect=orders:1234567890123456789 needs a count",
            "endpoint=x error until=0|must be a count of 1 or more",
            "endpoint=x error until=1h|is longer than camel.chaos.maxDuration",
            "endpoint=x error until=99999999999999999999|needs a whole number of at most 18 digits",
            "endpoint=x error until=2x|is not a time",
            "endpoint=x error within=1m|needs expect=",
            "endpoint=x error expect=orders|is not <route>:<count>",
            "endpoint=x error expect=:5|is not <route>:<count>",
            "endpoint=x error expect=orders:|is not <route>:<count>",
            "endpoint=x error expect=orders:0|needs a count of 1 or more",
            "endpoint=x error expect=orders:5 within=0s|must be longer than 0",
            "endpoint=x error expect=orders:5 within=11m|within=11m is longer than camel.chaos.maxDuration",
            "endpoint=x error expect=orders:5 within=106751991167d|is longer than camel.chaos.maxDuration",
            "endpoint=x error expect=orders:5 within=999999999999d|is not a time",
            "endpoint=x error message=|has no value",
            "endpoint= error|has no value",
            "=x error|has no name",
            "endpoint=x error message=\"abc|is not closed",
            "endpoint=x error message=\"abc\"def|is followed by text without a space",
            "endpoint=x error message=ab\"c|a quote can only start the value",
            "endpoint=x \"error\"|a quote can only start the value",
            "endpoint=x error me$sage=1|contains the character '$'",
            "endpoint=x error at=step|at=step is not a hook of the target endpoint=",
            "endpoint=x error at=step|(the hooks of endpoint= are: at=send offers error, delay, drop",
            "step=s1 error at=send|at=send is not a hook of the target step=",
            "step=s1 delay duration=1s at=send|at=send is not a hook of the target step=",
            "Endpoint=x error|a rule needs a target" })
    void rejectedWithReason(String rule, String reason) {
        rejected(rule, reason.replace("\\'", "'"));
    }

    @Test
    void exceptionParameterChecks() {
        String io = IOException.class.getName();
        rejected("endpoint=x delay duration=1s exception=" + io, "unknown parameter exception for the fault delay");
        rejected("endpoint=x error exception=" + io + " exception=" + io, "is given twice");
        rejected("endpoint=https:x error status=503 exception=" + io, "cannot be used with exception=");
    }

    @Test
    void exceptionClassChecks() {
        rejected("endpoint=x error exception=" + AbstractFailure.class.getName(), "is abstract or not public");
        rejected("endpoint=x error exception=" + NoConstructor.class.getName(), "has neither a public constructor");
        ChaosRule.Builder b = parse("endpoint=x error exception=" + NoArgOnly.class.getName());
        assertThat(b.exceptionTakesMessage).isFalse();
        assertThat(b.exceptionConstructor).isNotNull();
    }

    @Test
    void controlCharactersAndLengthAreRejected() {
        rejected("endpoint=x error message=\"a\nb\"", "control characters");
        rejected("endpoint=x error\u0000", "control characters");
        rejected("endpoint=x error message=" + "a".repeat(ChaosRuleParser.MAX_LENGTH), "at most 2000 are allowed");
        // the longest rule that is allowed
        String longest = "endpoint=x error message=" + "a".repeat(ChaosRuleParser.MAX_LENGTH - 25);
        assertThat(longest).hasSize(ChaosRuleParser.MAX_LENGTH);
        assertThat(parse(longest).message).hasSize(ChaosRuleParser.MAX_LENGTH - 25);
    }

    @Test
    void theReasonNamesTheRule() {
        assertThatThrownBy(() -> parse("route=orders error"))
                .hasMessageStartingWith("Invalid chaos rule [route=orders error]: ");
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void fuzzOnlyRejectsWithIllegalArgumentException() {
        // random rules built from the tokens and characters of the syntax: every input either parses or is rejected
        // with an IllegalArgumentException that has a reason, and nothing hangs
        String[] pieces = {
                "route=", "endpoint=", "component=", "step=", "error", "delay", "drop", "corrupt", "stall", "at=", "send",
                "poll", "entry", "step", "rate=", "every:", "until=", "expect=", "within=", "duration=", "mode=",
                "headers", "charset", "truncate", "exception=", "java.io.IOException", "message=", "status=", "503",
                "header.", "body=", "\"", "'", "\\", " ", " ", "\t", "=", ":", ";", "*", "0", "1", "0.5", "1m", "ms",
                "a", "orders", "x", "é", "\n", "\u0000", "$", "-", "." };
        Random random = new Random(42);
        int parsed = 0;
        for (int i = 0; i < 20_000; i++) {
            StringBuilder sb = new StringBuilder();
            int n = random.nextInt(12);
            for (int j = 0; j < n; j++) {
                sb.append(pieces[random.nextInt(pieces.length)]);
            }
            String rule = sb.toString();
            try {
                ChaosRule.Builder b = parse(rule);
                // a rule that parses has a target, a fault and at least one hook, or acts on a route (stall)
                assertThat(b.target).isNotNull();
                assertThat(b.fault).isNotNull();
                if (b.fault == Fault.LIFECYCLE) {
                    assertThat(b.target).isEqualTo(Target.ROUTE);
                    assertThat(b.hooks).isEmpty();
                } else {
                    assertThat(b.hooks).isNotEmpty();
                }
                parsed++;
            } catch (IllegalArgumentException e) {
                assertThat(e.getMessage()).as(rule).isNotBlank();
            }
            for (String part : ChaosRuleParser.splitRules(rule)) {
                assertThat(part).isNotBlank();
            }
        }
        assertThat(parsed).as("some random rules are valid").isPositive();
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void longAdversarialInputIsRejectedFast() {
        String quotes = "endpoint=x error message=\"" + "\\\"".repeat(900);
        assertThatThrownBy(() -> parse(quotes)).hasMessageContaining("is not closed");
        String equals = "endpoint=" + "=".repeat(1990);
        assertThatThrownBy(() -> parse(equals)).isInstanceOf(IllegalArgumentException.class);
        String words = "error ".repeat(333);
        assertThatThrownBy(() -> parse(words)).hasMessageContaining("has one fault");
        String stars = "endpoint=" + "*".repeat(1980) + " drop";
        assertThat(parse(stars).hooks).isEqualTo(Set.of(Hook.SEND, Hook.POLL));
    }

    public abstract static class AbstractFailure extends Exception {
        private static final long serialVersionUID = 1L;
    }

    public static class NoConstructor extends Exception {
        private static final long serialVersionUID = 1L;

        public NoConstructor(int code) {
            super("code " + code);
        }
    }

    public static class NoArgOnly extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
