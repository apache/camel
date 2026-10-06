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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The ERRORs on the screen of the Log tab for fix with AI (Shift+F8, CAMEL-25358), from the log lines of a failed
 * exchange as Camel writes them.
 */
class LogErrorsTest {

    private static final String DIRECT
            = "DirectConsumerNotAvailableException: No consumers available on endpoint: direct://big-orders.";

    /** The ERROR of a failed exchange, as the default error handler logs it in dev mode. */
    private static List<String> failedDelivery(String time, String exchangeId) {
        return List.of(
                "2026-10-05 " + time + " ERROR 29373 --- [ timer://orders] ocessor.errorhandler.DefaultErrorHandler : "
                       + "Failed delivery for (MessageId: " + exchangeId + " on ExchangeId: " + exchangeId
                       + "). Exhausted after delivery attempt: 1 caught: "
                       + "org.apache.camel.component.direct.DirectConsumerNotAvailableException: No consumers",
                "",
                "Message History",
                "--------------------------------------------------------------------------------------------",
                "Source                                   ID                             Processor              ",
                "orders.camel.yaml:4                      orders/orders                  from[timer://orders]   ",
                "orders.camel.yaml:20                     orders/choice1                 choice[when[simple]]   ",
                "orders.camel.yaml:24                     orders/to1                     to[direct:big-orders]  ",
                "",
                "Stacktrace",
                "--------------------------------------------------------------------------------------------",
                "org.apache.camel.component.direct.DirectConsumerNotAvailableException: No consumers available on "
                                                                                                                + "endpoint: direct://big-orders. Exchange["
                                                                                                                + exchangeId
                                                                                                                + "]",
                "\tat org.apache.camel.component.direct.DirectProducer.process(DirectProducer.java:83)",
                "\tat org.apache.camel.processor.SendProcessor.process(SendProcessor.java:172)");
    }

    private static List<String> info(String time, String message) {
        return List.of("2026-10-05 " + time + "  INFO 29373 --- [ timer://orders] orders.camel.yaml:18                     : "
                       + message);
    }

    private static List<LogEntry> entries(List<List<String>> records) {
        List<LogEntry> answer = new ArrayList<>();
        for (List<String> r : records) {
            for (String line : r) {
                answer.add(LogTab.parseLogLine(line));
            }
        }
        return answer;
    }

    @Test
    void theErrorOfAFailedExchangeHasItsSourceLineAndException() {
        List<LogEntry> log = entries(List.of(failedDelivery("19:57:30.116", "E50D-01")));

        List<LogErrors.LogError> errors = LogErrors.inView(log, 0, log.size());

        assertThat(errors).hasSize(1);
        LogErrors.LogError e = errors.get(0);
        assertThat(e.source()).isEqualTo("orders.camel.yaml:24");
        assertThat(e.exception()).startsWith(DIRECT);
        assertThat(e.logger()).isEqualTo("DefaultErrorHandler");
        assertThat(e.failure()).startsWith("ERROR in the log: " + DIRECT);
    }

    @Test
    void theSameErrorIsCountedOnceAndDifferentOnesNewestFirst() {
        List<String> other = List.of(
                "2026-10-05 19:58:40.000 ERROR 29373 --- [ timer://orders] org.acme.Billing                         : "
                                     + "Billing failed",
                "java.lang.IllegalStateException: no account 42",
                "\tat org.acme.Billing.charge(Billing.java:10)",
                "Caused by: java.net.ConnectException: Connection refused");
        List<LogEntry> log = entries(List.of(failedDelivery("19:57:30.116", "E50D-01"), info("19:57:33.000", "Order 2"),
                failedDelivery("19:58:15.134", "E50D-07"), other));

        List<LogErrors.LogError> errors = LogErrors.inView(log, 0, log.size());

        assertThat(errors).hasSize(2);
        assertThat(errors.get(0).logger()).isEqualTo("Billing");
        assertThat(errors.get(0).exception()).isEqualTo("IllegalStateException: no account 42");
        assertThat(errors.get(0).causes()).containsExactly("Caused by: ConnectException: Connection refused");
        assertThat(errors.get(0).source()).isNull();
        assertThat(errors.get(1).count()).isEqualTo(2);
        assertThat(errors.get(1).time()).isEqualTo("19:58:15.134");
        assertThat(errors.get(1).label()).startsWith("x2  19:58:15.134  " + DIRECT);
    }

    @Test
    void aStackTraceOnTheScreenBelongsToTheErrorAboveIt() {
        List<LogEntry> log = entries(List.of(info("19:57:29.000", "Order 1"), failedDelivery("19:57:30.116", "E50D-01"),
                info("19:57:33.000", "Order 2")));
        // the screen starts at the stack trace, below the ERROR line
        int start = log.size() - 3;

        List<LogErrors.LogError> errors = LogErrors.inView(log, start, 3);

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).source()).isEqualTo("orders.camel.yaml:24");
    }

    @Test
    void anErrorOfTheLogEipHasTheSourceLineOfItsLogger() {
        List<LogEntry> log = entries(List.of(List.of(
                "2026-10-05 20:14:59.906 ERROR 33838 --- [mer://inventory] orders.camel.yaml:44                     : "
                                                     + "Stock is low for item 7 in warehouse Copenhagen")));

        LogErrors.LogError e = LogErrors.inView(log, 0, log.size()).get(0);

        assertThat(e.source()).isEqualTo("orders.camel.yaml:44");
        assertThat(e.exception()).isNull();
        assertThat(e.failure()).isEqualTo("ERROR in the log: Stock is low for item 7 in warehouse Copenhagen");
    }

    @Test
    void noErrorOnTheScreen() {
        List<LogEntry> log = entries(List.of(failedDelivery("19:57:30.116", "E50D-01"), info("19:57:33.000", "Order 2"),
                info("19:57:36.000", "Order 3")));

        assertThat(LogErrors.inView(log, log.size() - 2, 2)).isEmpty();
        assertThat(LogErrors.inView(List.of(), 0, 10)).isEmpty();
    }

    @Test
    void theQuestionAboutAnErrorOfTheLog() {
        List<LogEntry> log = entries(List.of(List.of(
                "2026-10-05 19:58:40.000 ERROR 29373 --- [ timer://orders] org.acme.Billing                         : "
                                                     + "Billing failed",
                "java.lang.IllegalStateException: no account 42",
                "Caused by: java.net.ConnectException: Connection refused")));

        String q = AiFixPrompt.ofLogError(LogErrors.inView(log, 0, log.size()).get(0).text());

        assertThat(q).startsWith("Explain this ERROR from the log of the running integration:\n")
                .contains("ERROR Billing: Billing failed")
                .contains("IllegalStateException: no account 42")
                .contains("Caused by: ConnectException: Connection refused")
                .contains("camel_edit_file");
    }
}
