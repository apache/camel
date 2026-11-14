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
package org.apache.camel.dsl.jbang.core.commands.ai;

import java.util.List;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.ai.AnswerChecks.Mark;
import org.apache.camel.dsl.jbang.core.commands.ai.AnswerChecks.Problem;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * CAMEL-26960: the code blocks of an answer are checked for what does not exist, and the mistakes are marked under
 * their block. The snippets are from a benchmark with a local model (qwen3.6:35b, November 2026), which wrote them in
 * its answers to beginner questions.
 */
class AnswerCodeChecksTest {

    private static final CamelCatalog CATALOG = new DefaultCamelCatalog();

    @Test
    void aFromWithoutItsRouteIsChecked() {
        List<Mark> marks = AnswerChecks.checkCode(answer("yaml", """
                from:
                  uri: file:orders
                  steps:
                    - idempotentConsumer:
                        simple: "${header.CamelFileName}"
                        messageIdRepository: orderIds
                        steps:
                          - to: direct:audit
                """), CATALOG);
        assertThat(marks).extracting(Mark::block, Mark::line).containsExactly(tuple(1, 6));
        assertThat(marks.get(0).describe())
                .startsWith("code block 1, line 6: `messageIdRepository` is not an option of `idempotentConsumer`");
    }

    @Test
    void stepsAndOneEipAreCheckedInARoute() {
        List<Mark> marks = AnswerChecks.checkCode(answer("yaml", """
                - circuitBreaker:
                    forceOpen: false
                    failureThreshold: 50
                    steps:
                      - to: direct:supplier
                """), CATALOG);
        assertThat(marks).extracting(Mark::line).containsExactly(2, 3);
        assertThat(marks.get(0).message()).isEqualTo("`forceOpen` is not an option of `circuitBreaker`");
        // the validator's hint comes with the mark
        assertThat(marks.get(1).message()).startsWith("`failureThreshold` is not an option of `circuitBreaker` (")
                .contains("resilience4jConfiguration");

        marks = AnswerChecks.checkCode(answer("yaml", """
                idempotentConsumer:
                  simple: "${body[orderId]}"
                  messageIdRepository: orderIds
                """), CATALOG);
        assertThat(marks).extracting(Mark::line).containsExactly(3);
    }

    @Test
    void anOptionOfOneEipIsCheckedUnderIt() {
        List<Mark> marks = AnswerChecks.checkCode(answer("yaml", """
                redeliveryPolicy:
                  maximumRedeliveries: 3
                  redeliveryDelay: 1000
                  multiplier: 2          # 1s, 2s, 4s
                  useExponentialBackOff: true
                """), CATALOG);
        assertThat(marks).extracting(Mark::line).containsExactly(4);
        assertThat(marks.get(0).message()).startsWith("`multiplier` is not an option of `redeliveryPolicy`");
    }

    @Test
    void theOptionsOfAnEndpointAreChecked() {
        List<Mark> marks = AnswerChecks.checkCode(answer("yaml", """
                from:
                  uri: file:orders
                  parameters:
                    noop: true
                    deleteAfterRead: true
                  steps:
                    - to: "direct:audit?retryCount=3"
                """), CATALOG);
        assertThat(marks).extracting(Mark::line, Mark::message).containsExactly(
                tuple(5, "`deleteAfterRead` is not an option of the `file` component (did you mean `delete`?)"),
                tuple(7, "`retryCount` is not an option of the `direct` component"));

        // an option that is commented out is not one
        assertThat(AnswerChecks.checkCode(answer("yaml", """
                from:
                  uri: file
                  parameters:
                    directoryName: orders
                    # idempotentRepository: "#redisRepo"
                  steps:
                    - log: "${body}"
                """), CATALOG)).isEmpty();
    }

    @Test
    void theCamelPropertiesAreCheckedInAnyBlock() {
        // a model writes application.properties lines in a yaml block too
        List<Mark> marks = AnswerChecks.checkCode(answer("yaml", """
                # Add to application.properties
                camel.server.context-path=/api
                camel.jbang.dependencies=org.example:orders:1.0
                orders.directory=orders
                """), CATALOG);
        assertThat(marks).extracting(Mark::line).containsExactly(2);
        assertThat(marks.get(0).message()).startsWith("`camel.server.context-path` is not a Camel option (")
                .contains("camel.rest.contextPath");

        marks = AnswerChecks.checkCode(answer("properties", """
                camel.main.name=orders
                camel.component.file.orders-directory=orders
                """), CATALOG);
        assertThat(marks).extracting(Mark::line, Mark::message)
                .containsExactly(tuple(2, "`camel.component.file.orders-directory` is not a Camel option"));
    }

    @Test
    void anOnExceptionWrittenAsAListIsMarked() {
        // the exception class as a key: the validator knows the mistake, and says how to write it
        List<Mark> marks = AnswerChecks.checkCode(answer("yaml", """
                onException:
                  - java.lang.Exception:
                      handled: true
                      steps:
                        - to: direct:error-handler
                """), CATALOG);
        assertThat(marks).extracting(Mark::line).containsExactly(2);
        assertThat(marks.get(0).message()).startsWith("onException is a map, not a list");
    }

    @Test
    void whatIsNotAMistakeIsNotMarked() {
        // a route as the examples have it
        assertThat(marks("yaml", """
                - route:
                    id: orders
                    from:
                      uri: timer:orders?period=5000
                      steps:
                        - setBody:
                            simple: "order ${random(1000)}"
                        - log: "${body}"
                """)).isEmpty();
        // options shown without the EIP they are of: what they belong to is not known
        assertThat(marks("yaml", """
                slidingWindowSize: 10
                minimumNumberOfCalls: 5
                """)).isEmpty();
        // a required option and the steps left out, and a placeholder
        assertThat(marks("yaml", """
                - from:
                    uri: timer
                    steps:
                      - to: "{{orders.target}}"
                      - choice:
                          otherwise:
                            steps:
                              # your steps here
                """)).isEmpty();
        // the YAML of other tools
        assertThat(marks("yaml", """
                services:
                  kafka:
                    image: quay.io/strimzi/kafka:latest
                """)).isEmpty();
        assertThat(marks("yaml", """
                apiVersion: v1
                kind: ConfigMap
                metadata:
                  name: orders
                """)).isEmpty();
        assertThat(marks("yaml", """
                camel:
                  springboot:
                    name: orders
                """)).isEmpty();
        // a message body, and a block of another language
        assertThat(marks("yaml", """
                "lines": [{"sku": "CAMEL-TSHIRT", "qty": 1}]
                """)).isEmpty();
        assertThat(marks("text", """
                redeliveryPolicy:
                  multiplier: 2
                """)).isEmpty();
        // a block the model did not finish
        assertThat(AnswerChecks.checkCode("Retry with:\n```yaml\nredeliveryPolicy:\n  multiplier: 2\n", CATALOG))
                .isEmpty();
    }

    @Test
    void theMarksGoUnderTheirBlock() {
        String answer = """
                Two changes:

                1. Keep the order files:

                   ```yaml
                   from:
                     uri: file:orders?noop=true
                   ```
                2. Retry the call:

                   ```yaml
                   redeliveryPolicy:
                     multiplier: 2
                   ```
                That is all.""";
        List<Mark> marks = AnswerChecks.checkCode(answer, CATALOG);
        assertThat(marks).extracting(Mark::block, Mark::line).containsExactly(tuple(2, 2));

        String marked = AnswerChecks.withMarks(answer, marks);
        // the first block is as it was, and the mark of the second is under it, indented as its fence
        assertThat(marked).startsWith(answer.substring(0, answer.indexOf("2. Retry")));
        assertThat(marked).endsWith("     multiplier: 2\n   ```\n\n"
                                    + "   ✗ line 2: `multiplier` is not an option of `redeliveryPolicy`\n\nThat is all.");
        assertThat(AnswerChecks.withMarks(answer, List.of())).isEqualTo(answer);
    }

    @Test
    void theModelIsToldWhatIsLeft() {
        Mark mark = new Mark(2, 4, "`multiplier` is not an option of `redeliveryPolicy`");
        Problem problem = new Problem("${header.user ?: Guest}", "Other operator ?: does not accept Guest");
        assertThat(AnswerChecks.historyNote(List.of(mark), List.of(problem)))
                .contains("code block 2, line 4: `multiplier` is not an option of `redeliveryPolicy`")
                .contains("invalid simple expression ${header.user ?: Guest}: Other operator ?: does not accept Guest")
                .endsWith("Do not repeat them.");
        assertThat(AnswerChecks.historyNote(List.of(), List.of())).isNull();
    }

    private static List<Mark> marks(String language, String code) {
        return AnswerChecks.checkCode(answer(language, code), CATALOG);
    }

    private static String answer(String language, String code) {
        return "Here is how:\n\n```" + language + "\n" + code + "```\n\nThat should do it.";
    }
}
