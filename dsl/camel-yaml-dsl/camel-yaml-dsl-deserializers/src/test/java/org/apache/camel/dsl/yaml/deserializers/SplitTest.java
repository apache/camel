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
package org.apache.camel.dsl.yaml.deserializers;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

import org.apache.camel.dsl.yaml.common.YamlDeserializationContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.SplitDefinition;
import org.apache.camel.spi.Resource;
import org.junit.jupiter.api.Test;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

import static org.assertj.core.api.Assertions.assertThat;

class SplitTest {

    private static Resource dummyResource() {
        return new Resource() {
            @Override
            public String getScheme() {
                return "mem";
            }

            @Override
            public String getLocation() {
                return "test.yaml";
            }

            @Override
            public boolean exists() {
                return true;
            }

            @Override
            public InputStream getInputStream() {
                return new ByteArrayInputStream(new byte[0]);
            }
        };
    }

    @Test
    void split() {
        LoadSettings settings = LoadSettings.builder().build();

        YamlDeserializationContext ctr = new YamlDeserializationContext(settings);
        ctr.setCamelContext(new DefaultCamelContext());
        ctr.setResource(dummyResource());
        ctr.addResolver(new CustomResolver(new BeansDeserializer()));
        ctr.addResolver(new ModelDeserializersResolver());

        Load load = new Load(settings, ctr);

        @SuppressWarnings("unchecked")
        List<Object> result = (List<Object>) load.loadFromString("""
                - split:
                    simple: test
                    steps:
                      - to:
                          uri: to1
                      - to:
                          uri: to2
                """);

        assertThat(result).isNotNull();
        assertThat(result.size()).isEqualTo(1);

        SplitDefinition split = (SplitDefinition) result.get(0);
        assertThat(split.getOutputs().size()).isEqualTo(2);
        assertThat(split.getExpression()).isNotNull();
        assertThat(split.getExpression().getLanguage()).isEqualTo("simple");
        assertThat(split.getExpression().getExpression()).isEqualTo("test");
    }

    @Test
    void splitWithExpressionBlock() {
        LoadSettings settings = LoadSettings.builder().build();

        YamlDeserializationContext ctr = new YamlDeserializationContext(settings);
        ctr.setCamelContext(new DefaultCamelContext());
        ctr.setResource(dummyResource());
        ctr.addResolver(new CustomResolver(new BeansDeserializer()));
        ctr.addResolver(new ModelDeserializersResolver());

        Load load = new Load(settings, ctr);

        @SuppressWarnings("unchecked")
        List<Object> result = (List<Object>) load.loadFromString("""
                - split:
                    expression:
                      simple: test
                    steps:
                      - to:
                          uri: to1
                      - to:
                          uri: to2
                """);

        assertThat(result).isNotNull();
        assertThat(result.size()).isEqualTo(1);

        SplitDefinition split = (SplitDefinition) result.get(0);
        assertThat(split.getOutputs().size()).isEqualTo(2);
        assertThat(split.getExpression()).isNotNull();
        assertThat(split.getExpression().getLanguage()).isEqualTo("simple");
        assertThat(split.getExpression().getExpression()).isEqualTo("test");
    }
}
