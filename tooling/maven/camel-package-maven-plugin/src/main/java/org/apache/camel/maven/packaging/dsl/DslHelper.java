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
package org.apache.camel.maven.packaging.dsl;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.camel.tooling.model.ComponentModel;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.CaseUtils;

public final class DslHelper {
    private DslHelper() {
    }

    public static List<File> loadAllJavaFiles(final File dir, final String targetJavaPackageName) {
        final File allComponentsDslEndpointFactory = new File(dir, targetJavaPackageName.replace('.', '/'));
        return loadAllJavaFiles(allComponentsDslEndpointFactory);
    }

    public static List<File> loadAllJavaFiles(final File dir) {
        final File[] files = dir.listFiles();

        if (files == null) {
            return Collections.emptyList();
        }

        // load components
        return Arrays.stream(files)
                .filter(file -> file.isFile() && file.getName().endsWith(".java") && file.exists())
                .sorted()
                .collect(Collectors.toList());
    }

    public static String toCamelCaseLower(final String schema) {
        String convertedText = CaseUtils.toCamelCase(schema, false, '-', '+');
        if (convertedText != null) {
            switch (convertedText) {
                case "class":
                    convertedText = "clas";
                    break;
                case "package":
                    convertedText = "packag";
                    break;
                case "rest":
                    convertedText = "restEndpoint";
                    break;
                default:
                    break;
            }
        }
        return convertedText;
    }

    public static String getMainDescriptionWithoutPathOptions(final ComponentModel componentModel) {
        String desc = componentModel.getTitle() + " (" + componentModel.getArtifactId() + ")";
        desc += "\n" + componentModel.getDescription();
        desc += "\n";
        desc += "\nCategory: " + componentModel.getLabel();
        desc += "\nSince: " + componentModel.getFirstVersionShort();
        desc += "\nMaven coordinates: " + componentModel.getGroupId() + ":" + componentModel.getArtifactId();

        return desc;
    }

    public static String getMainDescription(final ComponentModel componentModel) {
        String desc = getMainDescriptionWithoutPathOptions(componentModel);
        // include javadoc for all path parameters and mark which are required
        desc += "\n";
        desc += "\nSyntax: <code>" + componentModel.getSyntax() + "</code>";

        for (ComponentModel.EndpointOptionModel option : componentModel.getEndpointOptions()) {
            if ("path".equals(option.getKind())) {
                desc += "\n";
                desc += "\nPath parameter: " + option.getName();
                if (option.isRequired()) {
                    desc += " (required)";
                }
                if (option.isDeprecated()) {
                    desc += " <strong>deprecated</strong>";
                }
                desc += "\n" + option.getDescription();
                if (option.getDefaultValue() != null) {
                    desc += "\nDefault value: " + option.getDefaultValue();
                }
                if (option.getEnums() != null && !option.getEnums().isEmpty()) {
                    desc += "\nThe value can be one of: " + wrapEnumValues(option.getEnums());
                }
            }
        }
        return desc;
    }

    public static String generateComponentBuilderClassName(final ComponentModel componentModel, final String suffix) {
        return StringUtils.capitalize(toCamelCaseLower(componentModel.getScheme()))
                + "Component" + suffix;
    }

    private static String wrapEnumValues(List<String> enumValues) {
        // comma to space so we can wrap words (which uses space)
        return String.join(", ", enumValues);
    }
}
