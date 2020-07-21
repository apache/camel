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
package org.apache.camel.maven.packaging.dsl.component;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;

import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.JsonMapper;
import org.apache.camel.tooling.util.PackageHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComponentsBuilderFactoryGeneratorTest {

    @Test
    public void testIfCreatesClassCorrectly() throws IOException {
        final String json = PackageHelper.loadText(new File(Objects.requireNonNull(getClass().getClassLoader().getResource("json/test_component.json")).getFile()));
        final ComponentModel componentModel = JsonMapper.generateComponentModel(json);

        final ComponentsBuilderFactoryGenerator componentsBuilderFactoryGenerator = ComponentsBuilderFactoryGenerator.generateClass(new HashSet<>(Collections.singletonList(componentModel)),
                getClass().getClassLoader(),
                "org.apache.camel.builder.component");

        final String classCode = componentsBuilderFactoryGenerator.printClassAsString();

        assertEquals("ComponentsBuilderFactory", componentsBuilderFactoryGenerator.getGeneratedClassName());

        assertTrue(classCode.contains("package org.apache.camel.builder.component;"));

        assertTrue(classCode.contains("static org.apache.camel.builder.component.dsl.KafkaComponentBuilderFactory.KafkaComponentBuilder kafka()"));
        assertTrue(classCode.contains("return org.apache.camel.builder.component.dsl.KafkaComponentBuilderFactory.kafka();"));
    }
}
