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

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The quick doc of an expression line of a YAML route says its language, as the Java and XML routes do.
 */
class YamlLanguageQuickDocTest {

    private static SourceEditAssist assist() {
        AtomicReference<List<IntegrationInfo>> data = new AtomicReference<>(List.of());
        AtomicReference<List<InfraInfo>> infraData = new AtomicReference<>(List.of());
        return new SourceEditAssist(new MonitorContext(data, infraData));
    }

    private static final List<String> YAML = List.of("""
            - route:
                from:
                  uri: "timer:tick"
                  steps:
                    - filter:
                        expression:
                          simple:
                            expression: "${header.foo} == 'bar'"
                        steps:
                          - setBody:
                              simple: "Hello ${body}"
            """.split("\\n"));

    @Test
    void theLanguageOfAnExpressionLine() {
        SourceEditAssist assist = assist();
        // the language key and the expression: under it, a predicate of the filter
        assertThat(assist.provideEditQuickDoc(YAML, 6)).extracting(SourceViewer.DocEntry::text)
                .containsExactly("Simple predicate: ${header.foo} == 'bar'");
        assertThat(assist.provideEditQuickDoc(YAML, 7)).extracting(SourceViewer.DocEntry::text)
                .containsExactly("Simple predicate: ${header.foo} == 'bar'");
        // the inline form, an expression of setBody
        assertThat(assist.provideEditQuickDoc(YAML, 10)).extracting(SourceViewer.DocEntry::text)
                .containsExactly("Simple expression: Hello ${body}");
        // the same text as the Java route has for its filter
        List<String> java = List.of("""
                public class MyRoute extends RouteBuilder {
                    public void configure() {
                        from("timer:tick")
                            .filter(simple("${header.foo} == 'bar'"))
                            .to("seda:out");
                    }
                }
                """.split("\\n"));
        assertThat(assist.provideRouteEditQuickDoc(Path.of("MyRoute.java"), java, 3))
                .extracting(SourceViewer.DocEntry::text).contains("Simple predicate: ${header.foo} == 'bar'");
    }
}
