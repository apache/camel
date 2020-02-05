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

import java.util.Objects;
import java.util.Set;

import javax.annotation.Generated;

import org.apache.camel.maven.packaging.AbstractGeneratorMojo;
import org.apache.camel.maven.packaging.ComponentDslMojo;
import org.apache.camel.maven.packaging.dsl.DslHelper;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.util.srcgen.JavaClass;
import org.apache.camel.tooling.util.srcgen.Method;

/**
 * Used to generate the main ComponentBuilderFactory which is the main entry of the DSLs.
 * E.g: ComponentBuilderFactory.kafka().setBrokers("{{host:port}}").build()
 */
public final class ComponentsBuilderFactoryGenerator {
    private static final String CLASS_NAME = "ComponentsBuilderFactory";

    private final String packageName;
    private final Set<ComponentModel> componentModels;
    private JavaClass javaClass;

    private ComponentsBuilderFactoryGenerator(final Set<ComponentModel> componentModels, final ClassLoader classLoader, final String packageName) {
        this.componentModels = componentModels;
        this.packageName = packageName;

        javaClass = new JavaClass(classLoader);
        // generate java class
        generateJavaClass();
    }

    public static ComponentsBuilderFactoryGenerator generateClass(final Set<ComponentModel> componentModels, final ClassLoader classLoader, final String packageName) {
        Objects.requireNonNull(componentModels);
        Objects.requireNonNull(classLoader);
        Objects.requireNonNull(packageName);

        return new ComponentsBuilderFactoryGenerator(componentModels, classLoader, packageName);
    }

    public String printClassAsString() {
        return javaClass.printClass(true);
    }

    @Override
    public String toString() {
        return printClassAsString();
    }

    public String getGeneratedClassName() {
        return CLASS_NAME;
    }

    private void generateJavaClass() {
        setPackage();
        setJavaDoc();
        setMainAnnotations();
        setBuilderFactoryClassNameAndType();
        setComponentsDslMethods();
    }

    private void setPackage() {
        javaClass.setPackage(packageName);
    }

    private void setJavaDoc() {
        final String doc = "Component entry DSL builder. You can build a component like this: ComponentBuilderFactory.kafka().setBrokers(\"{{host:port}}\").build()" + "\n\n"
                + AbstractGeneratorMojo.GENERATED_MSG;
        javaClass.getJavaDoc().setText(doc);
    }

    private void setMainAnnotations() {
        javaClass.addAnnotation(Generated.class).setStringValue("value", ComponentDslMojo.class.getName());
    }

    private void setBuilderFactoryClassNameAndType() {
        javaClass.setClass(false)
                .setPublic()
                .setName(getGeneratedClassName());
    }

    private void setComponentsDslMethods() {
        componentModels.forEach(componentModel -> {
            final String returnType = packageName + ".dsl." + ComponentDslBuilderFactoryGenerator.getExpectedGeneratedClassName(componentModel) + "."
                    + ComponentDslInnerBuilderGenerator.getExpectedGeneratedInterfaceName(componentModel);

            final Method componentEntryMethod = javaClass.addMethod();

            componentEntryMethod.setStatic()
                    .setReturnType(returnType)
                    .setName(DslHelper.toCamelCaseLower(componentModel.getScheme()))
                    .setBody(
                            String.format("return %s.dsl.%s.%s();", packageName,
                                    ComponentDslBuilderFactoryGenerator.getExpectedGeneratedClassName(componentModel),
                                    DslHelper.toCamelCaseLower(componentModel.getScheme())));

            if (componentModel.isDeprecated()) {
                componentEntryMethod.addAnnotation(Deprecated.class);
            }

            componentEntryMethod.getJavaDoc().setFullText(DslHelper.getMainDescriptionWithoutPathOptions(componentModel));
        });
    }
}
