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
package org.apache.camel.yaml.out;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.model.BeanFactoryDefinition;
import org.apache.camel.model.app.BeansDefinition;
import org.apache.camel.xml.in.ModelParser;
import org.apache.camel.yaml.LwModelToYAMLDumper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The beans dumped as YAML have their properties as the YAML DSL loads them: nested properties as a nested map
 * (CAMEL-25255).
 */
class YamlBeansDumpTest {

    @Test
    void nestedPropertiesAreANestedMap() throws Exception {
        BeansDefinition beans;
        try (InputStream in = Files.newInputStream(Paths.get("../camel-xml-io/src/test/resources/beansWithProperties.xml"))) {
            beans = new ModelParser(in, "http://camel.apache.org/schema/xml-io").parseBeansDefinition().orElseThrow();
        }
        String yaml = new LwModelToYAMLDumper().dumpBeansAsYaml(null, new ArrayList<>(beans.getBeans()));
        assertThat(yaml).isEqualTo("""
                - beans:
                    - name: b1
                      type: org.apache.camel.xml.in.ModelParserTest.MyBean
                      properties:
                        p1: v1
                        p2: v2
                        nested:
                          p1: v1a
                          p2: v2a
                    - name: b2
                      type: org.apache.camel.xml.in.ModelParserTest.MyBean
                      properties:
                        p1: v1
                        p2: v2
                        nested.p1: v1a
                        nested.p2: v2a
                """);
    }

    @Test
    void theClassPrefixOfTheTypeIsLeftOut() throws Exception {
        BeanFactoryDefinition<?> bean = new BeanFactoryDefinition<>();
        bean.setName("greeter");
        bean.setType("#class:com.foo.Greeter");
        String yaml = new LwModelToYAMLDumper().dumpBeansAsYaml(null, List.of(bean));
        assertThat(yaml).contains("type: com.foo.Greeter").doesNotContain("#class");
    }
}
