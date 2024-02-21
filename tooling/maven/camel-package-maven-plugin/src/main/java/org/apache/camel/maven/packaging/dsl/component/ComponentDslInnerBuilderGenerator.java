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

import org.apache.camel.maven.packaging.dsl.DslHelper;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.util.JavadocHelper;
import org.apache.camel.tooling.util.srcgen.JavaClass;
import org.apache.camel.tooling.util.srcgen.Method;
import org.apache.commons.lang3.StringUtils;

/**
 * DSL Generator class that generates component specific builder interface that contains the fluent methods, placed as
 * inner of the main component builder factory. E.g: KafkaComponentBuilderFactory.KafkaComponentBuilder
 */
public final class ComponentDslInnerBuilderGenerator {
    private static final String BUILDER_SUFFIX = "Builder";

    private final JavaClass javaClass;
    private final ComponentModel componentModel;

    private ComponentDslInnerBuilderGenerator(final JavaClass javaClass, final ComponentModel componentModel) {
        this.javaClass = javaClass;
        this.componentModel = componentModel;
        // generate class
        generateJavaClass();
    }

    public static ComponentDslInnerBuilderGenerator generateClass(
            final JavaClass javaClass, final ComponentModel componentModel) {
        return new ComponentDslInnerBuilderGenerator(javaClass, componentModel);
    }

    public static String getExpectedGeneratedInterfaceName(final ComponentModel componentModel) {
        return DslHelper.generateComponentBuilderClassName(componentModel, BUILDER_SUFFIX);
    }

    public String getGeneratedInterfaceName() {
        return getExpectedGeneratedInterfaceName(componentModel);
    }

    private void generateJavaClass() {
        setJavaDoc();
        setClassNameAndType();
        setFluentMethodsFromComponentOptions();
    }

    private void setJavaDoc() {
        javaClass.getJavaDoc().setText("Builder for the " + componentModel.getTitle() + " component.");
    }

    private void setClassNameAndType() {
        javaClass.setName(getGeneratedInterfaceName())
                .setPackagePrivate()
                .setClass(false)
                .extendSuperType(String.format("ComponentBuilder<%s>", componentModel.getShortJavaType()));
    }

    private void setFluentMethodsFromComponentOptions() {
        componentModel.getComponentOptions().forEach(componentOptionModel -> {
            final Method method = javaClass.addMethod();
            method.setDefault()
                    .setReturnType(getGeneratedInterfaceName())
                    .setName(StringUtils.uncapitalize(componentOptionModel.getName()))
                    .addParameter(componentOptionModel.getJavaType(), componentOptionModel.getName())
                    .setBody(String.format("doSetProperty(\"%s\", %s);", componentOptionModel.getName(),
                            componentOptionModel.getName()), "return this;");
            if (componentOptionModel.isDeprecated()) {
                method.addAnnotation(Deprecated.class);
            }
            String doc = generateOptionDescription(componentOptionModel);
            doc = JavadocHelper.xmlEncode(doc);
            doc += "\n\n@param " + componentOptionModel.getName() + " the value to set";
            doc += "\n@return the dsl builder\n";
            method.getJavaDoc().setText(doc);
        });
    }

    private String generateOptionDescription(final ComponentModel.ComponentOptionModel componentOptionModel) {
        String desc = JavadocHelper.xmlEncode(componentOptionModel.getDescription());
        if (!desc.endsWith(".")) {
            desc += ".";
        }
        if (componentOptionModel.isSupportFileReference()) {
            desc += "\n";
            desc += "\nThis option can also be loaded from an existing file, by prefixing with file: or classpath: followed by the location of the file.";
        }

        desc += "\n";
        desc += "\nThe option is a: <code>" + JavadocHelper.xmlEncode(componentOptionModel.getJavaType()) + "</code> type.";
        desc += "\n";
        if ("parameter".equals(componentOptionModel.getKind()) && componentOptionModel.isRequired()) {
            desc += "\nRequired: true";
        }
        // include default value (if any)
        if (componentOptionModel.getDefaultValue() != null) {
            // must xml encode description as in some rare cases it contains & chars which is invalid javadoc
            String text = JavadocHelper.xmlEncode(componentOptionModel.getDefaultValue().toString());
            desc += "\nDefault: " + text;
        }
        desc += "\nGroup: " + componentOptionModel.getGroup();

        return desc;
    }
}
