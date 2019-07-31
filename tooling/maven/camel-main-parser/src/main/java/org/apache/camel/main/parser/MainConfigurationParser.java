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
package org.apache.camel.main.parser;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.source.FieldSource;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import org.jboss.forge.roaster.model.source.MethodSource;

public class MainConfigurationParser {

    /**
     * Parses the Camel Main configuration java source file.
     */
    public List<ConfigurationModel> parseConfigurationSource(String fileName) throws FileNotFoundException {
        return parseConfigurationSource(new File(fileName));
    }

    /**
     * Parses the Camel Main configuration java source file.
     */
    public List<ConfigurationModel> parseConfigurationSource(File file) throws FileNotFoundException {
        final List<ConfigurationModel> answer = new ArrayList<>();

        JavaClassSource clazz = (JavaClassSource) Roaster.parse(file);
        List<FieldSource<JavaClassSource>> fields = clazz.getFields();
        // filter out final or static fields
        fields = fields.stream().filter(f -> !f.isFinal() && !f.isStatic()).collect(Collectors.toList());
        fields.forEach(f -> {
            String name = f.getName();
            String javaType = f.getType().getQualifiedName();
            String sourceType = clazz.getQualifiedName();
            String defaultValue = f.getStringInitializer();

            // the field must have a setter
            String setterName = "set" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
            MethodSource setter = clazz.getMethod(setterName, javaType);
            if (setter != null) {
                String desc = setter.getJavaDoc().getFullText();
                boolean deprecated = setter.getAnnotation(Deprecated.class) != null;
                ConfigurationModel model = new ConfigurationModel();
                model.setName(name);
                model.setJavaType(javaType);
                model.setDescription(desc);
                model.setSourceType(sourceType);
                model.setDefaultValue(defaultValue);
                model.setDeprecated(deprecated);
                answer.add(model);
            }
        });

        return answer;
    }
}
