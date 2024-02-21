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

package org.apache.camel.maven.packaging.generics;

import java.io.IOException;
import java.io.LineNumberReader;
import java.io.StringReader;
import java.util.List;

import org.apache.camel.tooling.model.ComponentModel;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.ASTNode;
import org.jboss.forge.roaster.model.source.MethodSource;

public final class JavadocUtil {
    private JavadocUtil() {

    }

    /**
     * @param  model the model from which the information are extracted.
     * @return       the description of the given component with all the possible details.
     */
    public static String getMainDescription(ComponentModel model) {
        return getMainDescription(model, true);
    }

    /**
     * @param  model                    the model from which the information are extracted.
     * @param  withPathParameterDetails indicates whether the information about the path parameters should be added to
     *                                  the description.
     * @return                          the description of the given component.
     */
    public static String getMainDescription(ComponentModel model, boolean withPathParameterDetails) {
        StringBuilder descSb = new StringBuilder(512);

        descSb.append(model.getTitle()).append(" (").append(model.getArtifactId()).append(")");
        descSb.append("\n").append(model.getDescription());
        descSb.append("\n\nCategory: ").append(model.getLabel());
        descSb.append("\nSince: ").append(model.getFirstVersionShort());
        descSb.append("\nMaven coordinates: ").append(model.getGroupId()).append(":").append(model.getArtifactId());

        if (withPathParameterDetails) {
            // include javadoc for all path parameters and mark which are required
            descSb.append("\n\nSyntax: <code>").append(model.getSyntax()).append("</code>");
            for (ComponentModel.EndpointOptionModel option : model.getEndpointOptions()) {
                if ("path".equals(option.getKind())) {
                    descSb.append("\n\nPath parameter: ").append(option.getName());
                    if (option.isRequired()) {
                        descSb.append(" (required)");
                    }
                    if (option.isDeprecated()) {
                        descSb.append(" <strong>deprecated</strong>");
                    }
                    descSb.append("\n").append(option.getDescription());
                    if (option.isSupportFileReference()) {
                        descSb.append(
                                "\nThis option can also be loaded from an existing file, by prefixing with file: or classpath: followed by the location of the file.");
                    }
                    if (option.getDefaultValue() != null) {
                        descSb.append("\nDefault value: ").append(option.getDefaultValue());
                    }
                    // TODO: default value note ?
                    if (option.getEnums() != null && !option.getEnums().isEmpty()) {
                        descSb.append("\nThere are ").append(option.getEnums().size())
                                .append(" enums and the value can be one of: ")
                                .append(wrapEnumValues(option.getEnums()));
                    }
                }
            }
        }

        return descSb.toString();
    }

    private static String wrapEnumValues(List<String> enumValues) {
        // comma to space so we can wrap words (which uses space)
        return String.join(", ", enumValues);
    }

    public static String pathParameterJavaDoc(ComponentModel model) {
        String doc = null;
        int pos = model.getSyntax().indexOf(':');
        if (pos != -1) {
            doc = model.getSyntax().substring(pos + 1);
        } else {
            doc = model.getSyntax();
        }

        // remove leading non alpha symbols
        char ch = doc.charAt(0);
        while (!Character.isAlphabetic(ch)) {
            doc = doc.substring(1);
            ch = doc.charAt(0);
        }
        return doc;
    }

    public static String extractJavaDoc(String sourceCode, MethodSource<?> ms) throws IOException {
        // the javadoc is mangled by roaster (sadly it does not preserve newlines and original formatting)
        // so we need to load it from the original source file
        Object internal = ms.getJavaDoc().getInternal();
        if (internal instanceof ASTNode) {
            int pos = ((ASTNode) internal).getStartPosition();
            int len = ((ASTNode) internal).getLength();
            if (pos > 0 && len > 0) {
                String doc = sourceCode.substring(pos, pos + len);
                LineNumberReader ln = new LineNumberReader(new StringReader(doc));
                String line;
                StringBuilder sb = new StringBuilder();
                while ((line = ln.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith("/**") || line.startsWith("*/")) {
                        continue;
                    }
                    if (line.startsWith("*")) {
                        line = line.substring(1).trim();
                    }
                    sb.append(line);
                    sb.append("\n");
                }
                doc = sb.toString();
                return doc;
            }
        }
        return null;
    }
}
