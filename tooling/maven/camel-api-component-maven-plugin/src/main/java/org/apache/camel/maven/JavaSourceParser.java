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
package org.apache.camel.maven;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.JavaDocTag;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import org.jboss.forge.roaster.model.source.MethodSource;
import org.jboss.forge.roaster.model.source.ParameterSource;

import static org.apache.camel.tooling.util.JavadocHelper.sanitizeDescription;

/**
 * Parses source java to get Method Signatures from Method Summary.
 */
public class JavaSourceParser {

    private List<String> methods = new ArrayList<>();
    private Map<String, String> methodText = new HashMap<>();
    private Map<String, Map<String, String>> parameters = new LinkedHashMap<>();
    private String errorMessage;

    public synchronized void parse(InputStream in) throws Exception {
        JavaClassSource clazz = (JavaClassSource) Roaster.parse(in);

        for (MethodSource ms : clazz.getMethods()) {
            // should not be constructor and must be public
            if (!ms.isPublic() || ms.isConstructor()) {
                continue;
            }
            String signature = ms.toSignature();
            // roaster signatures has return values at end
            // public create(String, AddressRequest) : Result

            int pos = signature.indexOf(':');
            if (pos != -1) {
                String result = signature.substring(pos + 1).trim();
                // lets use FQN types
                if (!"void".equals(result)) {
                    result = clazz.resolveType(result);
                }
                if (result == null || result.isEmpty()) {
                    result = "void";
                }
                if (Character.isUpperCase(result.charAt(0))) {
                    // okay so its maybe an inner class and has import so we need to resolve this more complex
                    pos = result.lastIndexOf('.');
                    if (pos != -1) {
                        String base = result.substring(0, pos);
                        String remainder = result.substring(pos + 1);
                        base = clazz.resolveType(base);
                        result = base + "$" + remainder;
                    } else {
                        result = result.replace('.', '$');
                        // okay no package name so its a local inner class
                        result = clazz.getPackage() + "." + result;
                    }
                }

                List<JavaDocTag> params = ms.getJavaDoc().getTags("@param");

                Map<String, String> docs = new LinkedHashMap<>();
                StringBuilder sb = new StringBuilder();
                sb.append("public ").append(result).append(" ").append(ms.getName()).append("(");
                List<ParameterSource> list = ms.getParameters();
                for (int i = 0; i < list.size(); i++) {
                    ParameterSource ps = list.get(i);
                    String name = ps.getName();
                    String type = ps.getType().getQualifiedNameWithGenerics();
                    type = clazz.resolveType(type);
                    if (Character.isUpperCase(type.charAt(0))) {
                        // okay so its maybe an inner class and has import so we need to resolve this more complex
                        pos = result.lastIndexOf('.');
                        if (pos != -1) {
                            String base = type.substring(0, pos);
                            String remainder = type.substring(pos + 1);
                            base = clazz.resolveType(base);
                            type = base + "$" + remainder;
                        } else {
                            type = type.replace('.', '$');
                            // okay no package name so its a local inner class
                            type = clazz.getPackage() + "." + type;
                        }
                    }
                    if (type.startsWith("java.lang.")) {
                        type = type.substring(10);
                    }
                    sb.append(type);
                    if (ps.isVarArgs() || ps.getType().isArray()) {
                        // the old way with javadoc did not use varargs in the signature, so lets transform this to an array style
                        sb.append("[]");
                    }
                    sb.append(" ").append(name);
                    if (i < list.size() - 1) {
                        sb.append(", ");
                    }

                    // need documentation for this parameter
                    docs.put(name, getJavadocValue(params, name));
                }
                sb.append(")");

                signature = sb.toString();
                Map<String, String> existing = parameters.get(ms.getName());
                if (existing != null) {
                    existing.putAll(docs);
                } else {
                    parameters.put(ms.getName(), docs);
                }
            }

            methods.add(signature);
            methodText.put(ms.getName(), signature);
        }
    }

    private static String getJavadocValue(List<JavaDocTag> params, String name) {
        for (JavaDocTag tag : params) {
            String key = tag.getValue();
            if (key.startsWith(name)) {
                String desc = key.substring(name.length());
                desc = sanitizeJavaDocValue(desc);
                return desc;
            }
        }
        return "";
    }

    private static String sanitizeJavaDocValue(String desc) {
        // remove leading - and whitespaces
        while (desc.startsWith("-")) {
            desc = desc.substring(1);
            desc = desc.trim();
        }
        desc = sanitizeDescription(desc, false);
        if (desc != null && !desc.isEmpty()) {
            // upper case first letter
            char ch = desc.charAt(0);
            if (Character.isAlphabetic(ch) && !Character.isUpperCase(ch)) {
                desc = Character.toUpperCase(ch) + desc.substring(1);
            }
            // remove ending dot if there is the text is just alpha or whitespace
            boolean removeDot = true;
            char[] arr = desc.toCharArray();
            for (int i = 0; i < arr.length; i++) {
                ch = arr[i];
                boolean accept = Character.isAlphabetic(ch) || Character.isWhitespace(ch) || ch == '\''
                        || ch == '-' || ch == '_';
                boolean last = i == arr.length - 1;
                accept |= last && ch == '.';
                if (!accept) {
                    removeDot = false;
                    break;
                }
            }
            if (removeDot && desc.endsWith(".")) {
                desc = desc.substring(0, desc.length() - 1);
            }
            desc = desc.trim();
        }
        return desc;
    }

    public void reset() {
        methods.clear();
        methodText.clear();
        parameters.clear();
        errorMessage = null;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public List<String> getMethods() {
        return methods;
    }

    public Map<String, String> getMethodText() {
        return methodText;
    }

    public Map<String, Map<String, String>> getParameters() {
        return parameters;
    }

}
