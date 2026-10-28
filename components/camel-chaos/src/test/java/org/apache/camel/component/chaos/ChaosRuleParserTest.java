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
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;

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
        assertThat(b.hooks).containsExactly(Hook.SEND);
    }

    @Test
    void errorWithoutParametersIsAChaosException() {
        ChaosRule.Builder b = parse("component=kafka error");
        assertThat(b.exceptionType).isNull();
        assertThat(b.hooks).containsExactly(Hook.SEND);
    }

    @Test
    void delayDropAndStepFaults() {
        ChaosRule.Builder delay = parse("endpoint=kafka:orders* delay duration=2s rate=0.2 until=5m");
        assertThat(delay.duration).isEqualTo(2000L);
        ChaosRule.Builder drop = parse("endpoint=file:inbox* drop at=send rate=0.5 until=2m");
        assertThat(drop.hooks).containsExactly(Hook.SEND);
        assertThat(drop.at).isEqualTo(Hook.SEND);
        assertThat(parse("step=enrich* delay duration=100ms").hooks).containsExactly(Hook.STEP);
        assertThat(parse("step=s1 error exception=" + IOException.class.getName()).exceptionType).isEqualTo(IOException.class);
    }

    @Test
    void quotedValuesAndEscapes() {
        assertThat(parse("endpoint=x error message='it is \\'down\\''").message).isEqualTo("it is 'down'");
        assertThat(parse("endpoint=x error message=\"a \\\\ b ; c\"").message).isEqualTo("a \\ b ; c");
        assertThat(parse("  endpoint=x\terror  ").fault).isEqualTo(Fault.ERROR);
    }

    @Test
    void splitRulesIsQuoteAware() {
        assertThat(ChaosRuleParser.splitRules("endpoint=a drop; endpoint=b error message=\"x;y\" ;;"))
                .containsExactly("endpoint=a drop", "endpoint=b error message=\"x;y\"");
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
            "endpoint=x|needs a fault",
            "endpoint=x error drop|has one fault",
            "endpoint=x step=y error|has one target",
            "endpoint=x explode|unknown fault explode",
            "step=s1 drop|is not offered",
            "endpoint=x error at=nowhere|unknown at=nowhere (the hooks are send and step)",
            "endpoint=x error foo=1|unknown parameter foo for the fault error",
            "endpoint=x delay|needs duration=",
            "endpoint=x delay duration=0|must be longer than 0",
            "endpoint=x delay duration=abc|is not a time",
            "endpoint=x delay duration=-5s|is not a time",
            "endpoint=x delay duration=11m|is longer than camel.chaos.maxDuration",
            "endpoint=x drop duration=1s|unknown parameter duration for the fault drop",
            "endpoint=x error exception=does.not.Exist|is not found",
            "endpoint=x error exception=java.lang.String|is not a Throwable",
            "endpoint=x error exception=java.lang.Exception;x|is not found",
            "endpoint=x error rate=0|is not a probability above 0",
            "endpoint=x error rate=1.5|is not a probability above 0",
            "endpoint=x error rate=-0.1|is neither a probability",
            "endpoint=x error rate=1e-3|is neither a probability",
            "endpoint=x error rate=NaN|is neither a probability",
            "endpoint=x error rate=0.1.2|is neither a probability",
            "endpoint=x error rate=.|is neither a probability",
            "endpoint=x error rate=every:0|needs a number of 1 or more",
            "endpoint=x error rate=every:x|needs a whole number",
            "endpoint=x error until=0|must be a count of 1 or more",
            "endpoint=x error until=1h|is longer than camel.chaos.maxDuration",
            "endpoint=x error until=99999999999999999999|needs a whole number of at most 18 digits",
            "endpoint=x error until=2x|is not a time",
            "endpoint=x error message=|has no value",
            "endpoint= error|has no value",
            "=x error|has no name",
            "endpoint=x error message=\"abc|is not closed",
            "endpoint=x error message=\"abc\"def|is followed by text without a space",
            "endpoint=x error message=ab\"c|a quote can only start the value",
            "endpoint=x \"error\"|a quote can only start the value",
            "endpoint=x error me$sage=1|contains the character '$'",
            "route=orders drop|a rule needs a target",
            "endpoint=x stall duration=20s|unknown fault stall",
            "endpoint=x error at=step|at=step is not a hook of the target endpoint=",
            "endpoint=x error at=step|(the hooks of endpoint= are: at=send offers error, delay, drop)",
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
        assertThatThrownBy(() -> parse("endpoint=x explode"))
                .hasMessageStartingWith("Invalid chaos rule [endpoint=x explode]: ");
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void fuzzOnlyRejectsWithIllegalArgumentException() {
        // random rules built from the tokens and characters of the syntax: every input either parses or is rejected
        // with an IllegalArgumentException that has a reason, and nothing hangs
        String[] pieces = {
                "endpoint=", "component=", "step=", "error", "delay", "drop", "at=", "send", "step", "rate=",
                "every:", "until=", "duration=",
                "exception=", "java.io.IOException", "message=",
                "\"", "'", "\\", " ", " ", "\t", "=", ":", ";", "*", "0", "1", "0.5", "1m", "ms",
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
                // a rule that parses has a target, a fault and at least one hook
                assertThat(b.target).isNotNull();
                assertThat(b.fault).isNotNull();
                assertThat(b.hooks).isNotEmpty();
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
        assertThat(parse(stars).hooks).isEqualTo(Set.of(Hook.SEND));
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
