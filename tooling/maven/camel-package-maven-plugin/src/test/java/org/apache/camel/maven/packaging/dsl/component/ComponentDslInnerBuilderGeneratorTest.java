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
import java.util.Objects;

import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.JsonMapper;
import org.apache.camel.tooling.util.PackageHelper;
import org.apache.camel.tooling.util.srcgen.JavaClass;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComponentDslInnerBuilderGeneratorTest {

    @Test
    public void testIfCreatesAllPropertiesCorrectly() throws IOException {
        final String json = PackageHelper.loadText(new File(Objects.requireNonNull(getClass().getClassLoader().getResource("json/test_component.json")).getFile()));
        final ComponentModel componentModel = JsonMapper.generateComponentModel(json);

        final JavaClass javaClass = new JavaClass();
        javaClass.setName("TestClass");

        final ComponentDslInnerBuilderGenerator componentDslInnerBuilderGenerator = ComponentDslInnerBuilderGenerator.generateClass(javaClass, componentModel);
        final String classCode = javaClass.printClass();

        // test for naming
        assertEquals("KafkaComponentBuilder", componentDslInnerBuilderGenerator.getGeneratedInterfaceName());
        assertTrue(classCode.contains("interface KafkaComponentBuilder extends ComponentBuilder<KafkaComponent>"));

        // test for properties
        componentModel.getComponentOptions().forEach(componentOptionModel -> {
            final String propertiesSettersMethods = "default KafkaComponentBuilder " + StringUtils.uncapitalize(componentOptionModel.getName());
            assertTrue(classCode.contains(propertiesSettersMethods));
        });
    }
}
