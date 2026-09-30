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
package org.apache.camel.dsl.yaml;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.dsl.yaml.common.YamlDeserializerBase;
import org.apache.camel.dsl.yaml.support.YamlTestSupport;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-24723: the compact notation is deprecated and the deserializers warn once per resource when they meet it, for a
 * language key directly on the EIP as much as for a step or a language written as a string.
 */
@Isolated
class CompactNotationWarnTest extends YamlTestSupport {

    private List<String> warnings;
    private AbstractAppender appender;

    @BeforeEach
    void setupLogging() {
        warnings = new ArrayList<>();
        appender = new AbstractAppender("compact-notation", null, null, false, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                if (event.getLevel() == Level.WARN) {
                    warnings.add(event.getMessage().getFormattedMessage());
                }
            }
        };
        appender.start();
        ((Logger) LogManager.getLogger(YamlDeserializerBase.class)).addAppender(appender);
    }

    @AfterEach
    void cleanupLogging() {
        ((Logger) LogManager.getLogger(YamlDeserializerBase.class)).removeAppender(appender);
        appender.stop();
    }

    @Test
    void aLanguageKeyDirectlyOnTheEipIsTheCompactNotation() throws Exception {
        loadRoutes("""
                - route:
                    from:
                      uri: "direct:start"
                      steps:
                        - setBody:
                            simple:
                              expression: "Hello ${body}"
                        - to:
                            uri: "mock:result"
                """);
        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0)).contains("YAML DSL compact notation detected", "camel validate normalize");
    }

    @Test
    void aStepWrittenAsAStringIsTheCompactNotation() throws Exception {
        loadRoutes("""
                - route:
                    from:
                      uri: "direct:start"
                      steps:
                        - log: "${body}"
                """);
        assertThat(warnings).hasSize(1);
    }

    @Test
    void aLanguageWrittenAsAStringIsTheCompactNotation() throws Exception {
        loadRoutes("""
                - route:
                    from:
                      uri: "direct:start"
                      steps:
                        - setBody:
                            expression:
                              constant: "Hello"
                """);
        assertThat(warnings).hasSize(1);
    }

    @Test
    void theCanonicalNotationIsNotWarnedAbout() throws Exception {
        loadRoutes("""
                - onException:
                    exception:
                      - java.lang.Exception
                    handled:
                      constant:
                        expression: "true"
                    steps:
                      - log:
                          message: "err"
                - route:
                    from:
                      uri: "direct:start"
                      steps:
                        - setBody:
                            expression:
                              simple:
                                expression: "Hello ${body}"
                        - choice:
                            when:
                              - expression:
                                  simple:
                                    expression: "${body} == 'x'"
                                steps:
                                  - log:
                                      message: "x"
                        - to:
                            uri: "mock:result"
                """);
        assertThat(warnings).isEmpty();
    }
}
