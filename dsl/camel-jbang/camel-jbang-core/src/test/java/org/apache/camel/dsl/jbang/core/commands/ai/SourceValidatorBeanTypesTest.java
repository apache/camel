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
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-24820: a bean whose class is created through its builder has its properties checked against the builder, and a
 * class with no constructor and no builder is reported with the ways to create it, before the run. The JDK classes
 * stand in for the LangChain4j, AWS SDK and Lombok classes the CLI cannot load.
 */
public class SourceValidatorBeanTypesTest {

    private static final CamelCatalog CATALOG = new DefaultCamelCatalog();

    @Test
    void builderPropertiesAreAccepted() {
        // java.net.http.HttpClient has no public constructor and a newBuilder(): created through the builder
        String yaml = """
                - beans:
                    - name: http
                      type: java.net.http.HttpClient
                      properties:
                        connectTimeout: 5s
                        follow-redirects: NORMAL
                """;
        assertThat(SourceValidator.validateBeanTypes(yaml)).isEmpty();
        assertThat(SourceValidator.validate("beans.camel.yaml", yaml, CATALOG, null)).isEmpty();
    }

    @Test
    void unknownBuilderPropertyNamesWhatTheBuilderAccepts() {
        String yaml = """
                - beans:
                    - name: http
                      type: "#class:java.net.http.HttpClient"
                      properties:
                        connectTimeout: 5s
                        timeout: 5s
                        connecttimeout: 2s
                """;
        List<String> msgs = SourceValidator.validateBeanTypes(yaml);
        assertThat(msgs).hasSize(2);
        assertThat(msgs.get(0))
                .startsWith("Line 6: timeout: unknown property of java.net.http.HttpClient, which is created through"
                            + " its builder java.net.http.HttpClient$Builder; the builder accepts: ")
                .contains("connectTimeout, cookieHandler, executor, followRedirects")
                .doesNotContain("did you mean");
        assertThat(msgs.get(1)).startsWith("Line 7: connecttimeout: unknown property of java.net.http.HttpClient"
                                           + " (did you mean connectTimeout?)");
    }

    @Test
    void aSetterOfTheCreatedBeanIsSuggestedToo() {
        String yaml = """
                - beans:
                    - name: widget
                      type: %s
                      properties:
                        Label: hello
                """.formatted(Widget.class.getName());
        List<String> msgs = SourceValidator.validateBeanTypes(yaml);
        assertThat(msgs).hasSize(1);
        assertThat(msgs.get(0))
                .startsWith("Line 5: Label: unknown property of " + Widget.class.getName() + " (did you mean label?)")
                .endsWith("; the builder accepts: size; the created bean accepts: label");
    }

    @Test
    void builderMethodMustExistOnTheBuilder() {
        String yaml = """
                - beans:
                    - name: http
                      type: java.net.http.HttpClient
                      builderMethod: create
                """;
        List<String> msgs = SourceValidator.validateBeanTypes(yaml);
        assertThat(msgs).hasSize(1);
        assertThat(msgs.get(0)).isEqualTo("Line 4: builderMethod: java.net.http.HttpClient$Builder has no public no-arg"
                                          + " method create(); its methods returning HttpClient are: build");
    }

    @Test
    void noConstructorAndNoBuilderNamesTheWaysToCreateIt() {
        // java.lang.Runtime has a private constructor and a static getRuntime()
        String yaml = """
                - beans:
                    - name: rt
                      type: java.lang.Runtime
                """;
        List<String> msgs = SourceValidator.validateBeanTypes(yaml);
        assertThat(msgs).hasSize(1);
        assertThat(msgs.get(0))
                .startsWith("Line 3: type: class java.lang.Runtime has no public no-arg constructor and no builder()"
                            + " or newBuilder() method. Create it with factoryMethod (and constructors: for its arguments)"
                            + " for the static getRuntime()")
                .endsWith(", or with a builder class of its own (builderClass and builderMethod)");
    }

    @Test
    void explicitCreationAndUnknownClassesAreLeftAlone() {
        String yaml = """
                - beans:
                    - name: rt
                      type: java.lang.Runtime
                      factoryMethod: getRuntime
                    - name: list
                      type: java.util.ArrayList
                      properties:
                        anything: goes
                    - name: model
                      type: dev.langchain4j.model.ollama.OllamaChatModel
                      properties:
                        modelName: qwen2.5
                    - name: placeholder
                      type: "{{bean.class}}"
                    - name: http2
                      type: java.net.http.HttpClient
                      builderClass: com.example.MyHttpClientBuilder
                      properties:
                        anything: goes
                """;
        assertThat(SourceValidator.validateBeanTypes(yaml)).isEmpty();
    }

    /** Created through its builder, with a setter of its own: the label is a property of the bean, not the builder */
    public static final class Widget {
        private String label;

        private Widget() {
        }

        public static Builder builder() {
            return new Builder();
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        public static final class Builder {
            public Builder size(int size) {
                return this;
            }

            public Widget build() {
                return new Widget();
            }
        }
    }
}
