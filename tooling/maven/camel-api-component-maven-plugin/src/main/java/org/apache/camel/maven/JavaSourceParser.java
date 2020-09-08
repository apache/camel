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
import java.util.StringJoiner;

import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.JavaDocTag;
import org.jboss.forge.roaster.model.Type;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import org.jboss.forge.roaster.model.source.MethodSource;
import org.jboss.forge.roaster.model.source.ParameterSource;
import org.jboss.forge.roaster.model.source.TypeVariableSource;

import static org.apache.camel.tooling.util.JavadocHelper.sanitizeDescription;

/**
 * Parses source java to get Method Signatures from Method Summary.
 */
public class JavaSourceParser {

    private List<String> methods = new ArrayList<>();
    private Map<String, String> methodText = new HashMap<>();
    private Map<String, Map<String, String>> parameters = new LinkedHashMap<>();
    private String errorMessage;

    public synchronized void parse(InputStream in, String innerClass) throws Exception {
        JavaClassSource rootClazz = (JavaClassSource) Roaster.parse(in);
        JavaClassSource clazz = rootClazz;

        if (innerClass != null) {
            // we want the inner class from the parent class
            clazz = findInnerClass(rootClazz, innerClass);
            if (clazz == null) {
                errorMessage = "Cannot find inner class " + innerClass + " in class: " + rootClazz.getQualifiedName();
                return;
            }
        }

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
                    result = resolveType(rootClazz, clazz, result);
                }
                if (result.isEmpty()) {
                    result = "void";
                }

                List<JavaDocTag> params = ms.getJavaDoc().getTags("@param");

                Map<String, String> docs = new LinkedHashMap<>();
                StringBuilder sb = new StringBuilder();
                sb.append("public ").append(result).append(" ").append(ms.getName()).append("(");
                List<ParameterSource> list = ms.getParameters();
                for (int i = 0; i < list.size(); i++) {
                    ParameterSource ps = list.get(i);
                    String name = ps.getName();
                    String type = resolveType(rootClazz, clazz, ms, ps.getType());
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

    private static JavaClassSource findInnerClass(JavaClassSource rootClazz, String innerClass) {
        String[] parts = innerClass.split("\\$");
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            JavaClassSource nested = (JavaClassSource) rootClazz.getNestedType(part);
            if (nested != null && i < parts.length - 1) {
                rootClazz = nested;
            } else {
                return nested;
            }
        }
        return null;
    }

    private static String resolveType(JavaClassSource rootClazz, JavaClassSource clazz, MethodSource ms, Type type) {
        String name = type.getName();
        // if the type is from a type variable (eg T extends Foo generic style)
        // then the type should be returned as-is
        TypeVariableSource tv = ms.getTypeVariable(name);
        if (tv == null) {
            clazz.getTypeVariable(name);
        }
        if (tv != null) {
            return type.getName();
        }

        String answer = resolveType(rootClazz, clazz, name);
        List<Type> types = type.getTypeArguments();
        if (!types.isEmpty()) {
            if (type.isArray()) {
                answer = type.getQualifiedNameWithGenerics();
            } else {
                StringJoiner sj = new StringJoiner(", ");
                for (Type arg : types) {
                    sj.add(resolveType(rootClazz, clazz, ms, arg));
                }
                answer = answer + "<" + sj.toString() + ">";
            }
        }
        return answer;
    }

    private static String resolveType(JavaClassSource rootClazz, JavaClassSource clazz, String type) {
        if ("void".equals(type)) {
            return "void";
        }

        // workaround bug in Roaster about resolving type that was an inner class
        // is this an inner class
        boolean inner = rootClazz.getNestedType(type) != null;
        if (inner) {
            return rootClazz.getQualifiedName() + "$" + type;
        }
        inner = clazz.getNestedType(type) != null;
        if (inner) {
            return clazz.getQualifiedName() + "$" + type;
        }
        int dot = type.indexOf('.');
        if (Character.isUpperCase(type.charAt(0)) && dot != -1) {
            // okay its likely a inner class with a nested sub type, so resolving is even more complex
            String parent = type.substring(0, dot);
            String child = type.substring(dot + 1);
            inner = rootClazz.getNestedType(parent) != null;
            if (inner) {
                return rootClazz.getQualifiedName() + "$" + type.replace('.', '$');
            }
            inner = clazz.getNestedType(type) != null;
            if (inner) {
                return clazz.getQualifiedName() + "$" + type.replace('.', '$');
            }
            if (parent.equals(rootClazz.getName())) {
                inner = rootClazz.getNestedType(child) != null;
                if (inner) {
                    return rootClazz.getQualifiedName() + "$" + child.replace('.', '$');
                }
                inner = clazz.getNestedType(child) != null;
                if (inner) {
                    return clazz.getQualifiedName() + "$" + child.replace('.', '$');
                }
            }
            String resolvedType = rootClazz.resolveType(parent);
            return resolvedType + "$" + child;
        }

        // okay attempt to resolve the type
        String resolvedType = clazz.resolveType(type);
        if (resolvedType.equals(type)) {
            resolvedType = rootClazz.resolveType(type);
        }
        return resolvedType;
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
        desc = desc.trim();
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
