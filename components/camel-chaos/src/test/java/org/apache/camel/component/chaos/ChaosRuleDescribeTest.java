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

import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.camel.component.chaos.ChaosRule.Hook;
import org.apache.camel.impl.engine.DefaultClassResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A rule in plain words ({@link ChaosRule#describe()}): every target, hook and fault the parser takes, the rates, the
 * times and the expectations.
 */
class ChaosRuleDescribeTest {

    private static final long MAX = TimeUnit.DAYS.toMillis(1);

    private static ChaosRule rule(String text) {
        return new ChaosRule(ChaosRuleParser.parse(text, new DefaultClassResolver(), MAX), "r1", 42, 0, MAX);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|',
               textBlock = """
                       endpoint=http:stock* error status=503 rate=0.5 until=5m | fail half of the sends to http:stock* with HTTP status 503, for 5 minutes
                       endpoint=kafka:orders* error rate=0.1 | fail 10% of the sends and polls of kafka:orders*
                       endpoint=kafka:orders* delay duration=2s rate=0.2 until=5m | delay 20% of the sends and polls of kafka:orders* by 2 seconds, for 5 minutes
                       endpoint=file:inbox* drop at=poll rate=0.5 until=2m | drop half of the polls of file:inbox*, for 2 minutes
                       endpoint=file:inbox* drop at=send | drop every send to file:inbox*
                       step=enrich* error exception=java.sql.SQLException rate=0.1 | fail 10% of the runs of step enrich* with SQLException
                       step=enrich delay duration=1h30m | delay every run of step enrich by 1 hour 30 minutes
                       endpoint=seda:dlq error at=dlq | fail every dead letter send to seda:dlq
                       endpoint=http:slow* error exception=java.net.SocketTimeoutException after=5s at=send | fail every send to http:slow* with SocketTimeoutException after waiting 5 seconds
                       endpoint=mock:x delay duration=100ms..2s at=send | delay every send to mock:x by 100 milliseconds to 2 seconds
                       endpoint=mock:x delay duration=1500ms at=poll | delay every poll of mock:x by 1.5 seconds
                       component=kafka duplicate rate=0.05 | duplicate 5% of the sends to the kafka endpoints
                       component=http error status=500 rate=0.333 | fail 33.3% of the sends to the http endpoints with HTTP status 500
                       route=orders corrupt mode=headers | corrupt every message entering route orders with random header values
                       route=order* corrupt mode=empty rate=every:3 | corrupt 1 in 3 messages entering routes order* by emptying the body
                       endpoint=http:x corrupt mode=truncate at=reply | corrupt every reply from http:x by cutting the body in half
                       endpoint=x corrupt mode=charset | corrupt every send to x by encoding the text body in UTF-16
                       endpoint=x corrupt mode=remove-header | corrupt every send to x by removing its headers
                       endpoint=x corrupt mode=remove-header header=Authorization | corrupt every send to x by removing the header Authorization
                       endpoint=x corrupt mode=remove-header header=X-Trace-* | corrupt every send to x by removing the headers X-Trace-*
                       endpoint=x error rate=every:1 at=send | fail every send to x
                       endpoint=x error message=boom at=send | fail every send to x with "boom"
                       endpoint=x error until=1 at=send | fail every send to x, for the next send
                       endpoint=x error rate=every:5 until=100 | fail 1 in 5 sends and polls of x, for the next 100 matches
                       endpoint=x error rate=burst:5s/30s until=10m at=send | fail every send to x, during the first 5 seconds of every 30 seconds, for 10 minutes
                       endpoint=x error from=+30s until=1m at=send expect=orders:100 | fail every send to x, starting after 30 seconds, for 1 minute; expects at least 100 exchanges completed in route orders
                       endpoint=x error at=send expect=orders:handled<=0 within=2m | fail every send to x; expects no exchanges handled by the error handler in route orders within 2 minutes
                       endpoint=x error at=send expect=orders:failed=3 | fail every send to x; expects exactly 3 exchanges failed in route orders
                       endpoint=x error at=send expect=orders:redeliveries>=2 | fail every send to x; expects at least 2 redeliveries in route orders
                       endpoint=x error at=send expect=orders:completed<=1 | fail every send to x; expects at most 1 exchange completed in route orders
                       endpoint=http:stock* in=orders error status=503 rate=0.5 | fail half of the sends to http:stock* in route orders with HTTP status 503
                       step=* in=order* delay duration=500ms | delay every run of step * in routes order* by 500 milliseconds
                       component=file drop at=poll in=inbox | drop every poll of the file endpoints in route inbox
                       route=orders error rate=0.05 | fail 5% of the messages entering route orders
                       route=orders error exception=java.net.ConnectException | fail every message entering route orders with ConnectException
                       route=order* delay duration=2s until=10 | delay every message entering routes order* by 2 seconds, for the next 10 messages
                       endpoint=http:stock* error status=503 when="${header.country} == 'ES'" | fail every send to http:stock* with HTTP status 503, when ${header.country} == 'ES'
                       route=orders error rate=0.05 when='${header.vip}' until=10m | fail 5% of the messages entering route orders, when ${header.vip}, for 10 minutes
                       route=poller stall duration=20s | suspend route poller for 20 seconds
                       route=poller lifecycle action=stop duration=1m30s | stop route poller for 1 minute 30 seconds
                       route=poller lifecycle action=remove duration=30s repeat=3 every=1m from=+10s | remove route poller for 30 seconds, then add it back, 3 times, every 1 minute, starting after 10 seconds
                       route=poller stall duration=20s expect=orders:10 within=5m | suspend route poller for 20 seconds; expects at least 10 exchanges completed in route orders within 5 minutes
                       """)
    void describe(String text, String words) {
        assertThat(rule(text).describe()).isEqualTo(words);
    }

    @Test
    void describeOnlyTheHooksThatCanFire() {
        ChaosRule rule = rule("endpoint=kafka:orders* error rate=0.1");
        assertThat(rule.describe(Set.of(Hook.SEND))).isEqualTo("fail 10% of the sends to kafka:orders*");
        assertThat(rule.describe(Set.of(Hook.POLL))).isEqualTo("fail 10% of the polls of kafka:orders*");
        // where it can fire nowhere, the words name all its hooks
        assertThat(rule.describe(Set.of())).isEqualTo(rule.describe());
    }

    @Test
    void theExamplesOfTheDocs() {
        // chaos.adoc, "A rule in plain words": a Kafka consumer does not poll on a schedule, so only the sends
        assertThat(rule("endpoint=kafka:orders* delay duration=100ms..2s rate=0.2").describe(Set.of(Hook.SEND)))
                .isEqualTo("delay 20% of the sends to kafka:orders* by 100 milliseconds to 2 seconds");
        assertThat(rule("endpoint=file:inbox* drop at=poll rate=every:10 until=100").describe())
                .isEqualTo("drop 1 in 10 polls of file:inbox*, for the next 100 polls");
        assertThat(rule("component=kafka duplicate rate=0.05 from=+30s").describe())
                .isEqualTo("duplicate 5% of the sends to the kafka endpoints, starting after 30 seconds");
        assertThat(rule("route=orders corrupt mode=headers rate=burst:5s/30s").describe())
                .isEqualTo("corrupt every message entering route orders with random header values, during the first"
                           + " 5 seconds of every 30 seconds");
        assertThat(rule("step=enrich* error exception=java.sql.SQLException expect=orders:handled<=0").describe())
                .isEqualTo("fail every run of step enrich* with SQLException; expects no exchanges handled by the"
                           + " error handler in route orders");
    }

    @Test
    void words() {
        assertThat(ChaosWords.time(1)).isEqualTo("1 millisecond");
        assertThat(ChaosWords.time(500)).isEqualTo("500 milliseconds");
        assertThat(ChaosWords.time(1000)).isEqualTo("1 second");
        assertThat(ChaosWords.time(2250)).isEqualTo("2.25 seconds");
        assertThat(ChaosWords.time(61_000)).isEqualTo("1 minute 1 second");
        assertThat(ChaosWords.time(TimeUnit.HOURS.toMillis(2))).isEqualTo("2 hours");
        assertThat(ChaosWords.time(TimeUnit.DAYS.toMillis(1) + 5)).isEqualTo("1 day 5 milliseconds");
        assertThat(ChaosWords.percent(0.3)).isEqualTo("30%");
        assertThat(ChaosWords.percent(0.005)).isEqualTo("0.5%");
        assertThat(ChaosWords.percent(1.0d / 3)).isEqualTo("33.33%");
    }
}
