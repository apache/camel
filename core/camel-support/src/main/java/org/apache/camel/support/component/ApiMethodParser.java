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
package org.apache.camel.support.component;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parser base class for generating ApiMethod enumerations.
 */
public abstract class ApiMethodParser<T> {

    private static final String METHOD_PREFIX
            = "^(\\s*(public|final|synchronized|native)\\s+)*(\\s*<((?!\\sextends\\s)[^>])+>)?\\s*(\\S+)\\s+([^\\(]+\\s*)\\(";

    private static final String JAVA_LANG = "java.lang.";
    private static final Map<String, Class<?>> PRIMITIVE_TYPES;

    static {
        PRIMITIVE_TYPES = new HashMap<>();
        PRIMITIVE_TYPES.put("int", Integer.TYPE);
        PRIMITIVE_TYPES.put("long", Long.TYPE);
        PRIMITIVE_TYPES.put("double", Double.TYPE);
        PRIMITIVE_TYPES.put("float", Float.TYPE);
        PRIMITIVE_TYPES.put("boolean", Boolean.TYPE);
        PRIMITIVE_TYPES.put("char", Character.TYPE);
        PRIMITIVE_TYPES.put("byte", Byte.TYPE);
        PRIMITIVE_TYPES.put("void", Void.TYPE);
        PRIMITIVE_TYPES.put("short", Short.TYPE);
    }

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final Class<T> proxyType;
    private List<String> signatures;
    private final Map<String, Map<String, String>> signaturesArguments = new HashMap<>();
    private Map<String, Map<String, String>> parameters;
    private final Map<String, String> descriptions = new HashMap<>();
    private ClassLoader classLoader = ApiMethodParser.class.getClassLoader();

    public ApiMethodParser(Class<T> proxyType) {
        this.proxyType = proxyType;
    }

    public Class<T> getProxyType() {
        return proxyType;
    }

    public final List<String> getSignatures() {
        return signatures;
    }

    public final void setSignatures(List<String> signatures) {
        this.signatures = new ArrayList<>();
        this.signatures.addAll(signatures);
    }

    public Map<String, Map<String, String>> getSignaturesArguments() {
        return signaturesArguments;
    }

    public void addSignatureArguments(String name, Map<String, String> arguments) {
        this.signaturesArguments.put(name, arguments);
    }

    public Map<String, String> getDescriptions() {
        return descriptions;
    }

    public void setDescriptions(Map<String, String> descriptions) {
        this.descriptions.putAll(descriptions);
    }

    public Map<String, Map<String, String>> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, Map<String, String>> parameters) {
        this.parameters = parameters;
    }

    public final ClassLoader getClassLoader() {
        return classLoader;
    }

    public final void setClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    /**
     * Parses the method signatures from {@code getSignatures()}.
     *
     * @return list of Api methods as {@link ApiMethodModel}
     */
    public final List<ApiMethodModel> parse() {
        // parse sorted signatures and generate descriptions
        List<ApiMethodModel> result = new ArrayList<>();
        for (String signature : signatures) {

            // skip comment or empty lines
            if (signature.startsWith("##") || ObjectHelper.isEmpty(signature)) {
                continue;
            }

            // remove all modifiers and type parameters for method
            signature = signature.replaceAll(METHOD_PREFIX, "$5 $6(");
            // remove all final modifiers for arguments
            signature = signature.replaceAll("(\\(|,\\s*)final\\s+", "$1");
            // remove all redundant spaces in generic parameters
            signature = signature.replaceAll("\\s*<\\s*", "<").replaceAll("\\s*>", ">");

            log.debug("Processing {}", signature);

            final List<ApiMethodArg> arguments = new ArrayList<>();
            final List<Class<?>> argTypes = new ArrayList<>();

            // Map<String, Map<XXX, Bla>> foo(
            int space = 0;
            int max = signature.indexOf('(');
            for (int i = max; i > 0; i--) {
                char ch = signature.charAt(i);
                if (Character.isWhitespace(ch)) {
                    space = i;
                    break;
                }
            }
            final String name = signature.substring(space, max).trim();
            String rt = signature.substring(0, space).trim();
            // remove generic so the type is just the class name
            int pos = rt.indexOf('<');
            if (pos != -1) {
                rt = rt.substring(0, pos);
            }
            final String returnType = rt;
            final Class<?> resultType = forName(returnType);

            // use the signature arguments from the parser so we do not have to use our own magic regexp parsing that is flawed
            Map<String, String> args = signaturesArguments.get(signature);
            if (args != null) {
                for (Map.Entry<String, String> entry : args.entrySet()) {
                    String argName = entry.getKey();
                    String rawTypeArg = entry.getValue();
                    String shortTypeArgs = rawTypeArg;
                    String typeArg = null;
                    // handle generics
                    pos = shortTypeArgs.indexOf('<');
                    if (pos != -1) {
                        typeArg = shortTypeArgs.substring(pos);
                        // remove leading and trailing < > as that is what the old way was doing
                        if (typeArg.startsWith("<")) {
                            typeArg = typeArg.substring(1);
                        }
                        if (typeArg.endsWith(">")) {
                            typeArg = typeArg.substring(0, typeArg.length() - 1);
                        }
                        shortTypeArgs = shortTypeArgs.substring(0, pos);
                    }
                    final Class<?> type = forName(shortTypeArgs);
                    argTypes.add(type);

                    String typeDesc = null;
                    if (parameters != null && name != null && argName != null) {
                        Map<String, String> params = parameters.get(name);
                        if (params != null) {
                            typeDesc = params.get(argName);
                        }
                    }
                    arguments.add(new ApiMethodArg(argName, type, typeArg, rawTypeArg, typeDesc));
                }
            }

            Method method;
            try {
                method = proxyType.getMethod(name, argTypes.toArray(new Class<?>[0]));
            } catch (NoSuchMethodException e) {
                throw new IllegalArgumentException("Method not found [" + signature + "] in type " + proxyType.getName());
            }
            result.add(new ApiMethodModel(name, resultType, arguments, method, descriptions.get(name), signature));
        }

        // allow derived classes to post process
        result = processResults(result);

        // check that argument names have the same type across methods
        Map<String, Class<?>> allArguments = new HashMap<>();
        for (ApiMethodModel model : result) {
            for (ApiMethodArg argument : model.getArguments()) {
                String name = argument.getName();
                Class<?> argClass = allArguments.get(name);
                Class<?> type = argument.getType();
                if (argClass == null) {
                    allArguments.put(name, type);
                } else {
                    if (argClass != type) {
                        throw new IllegalArgumentException(
                                "Argument [" + name
                                                           + "] is used in multiple methods with different types "
                                                           + argClass.getCanonicalName() + ", " + type.getCanonicalName());
                    }
                }
            }
        }
        allArguments.clear();

        result.sort(new Comparator<ApiMethodModel>() {
            @Override
            public int compare(ApiMethodModel model1, ApiMethodModel model2) {
                final int nameCompare = model1.name.compareTo(model2.name);
                if (nameCompare != 0) {
                    return nameCompare;
                } else {

                    final int nArgs1 = model1.arguments.size();
                    final int nArgsCompare = nArgs1 - model2.arguments.size();
                    if (nArgsCompare != 0) {
                        return nArgsCompare;
                    } else {
                        // same number of args, compare arg names, kinda arbitrary to use alphabetized order
                        for (int i = 0; i < nArgs1; i++) {
                            final int argCompare
                                    = model1.arguments.get(i).getName().compareTo(model2.arguments.get(i).getName());
                            if (argCompare != 0) {
                                return argCompare;
                            }
                        }
                        // duplicate methods???
                        log.warn("Duplicate methods found [{}], [{}]", model1, model2);
                        return 0;
                    }
                }
            }
        });

        // assign unique names to every method model
        final Map<String, Integer> dups = new HashMap<>();
        for (ApiMethodModel model : result) {
            // locale independent upper case conversion
            String uniqueName = StringHelper.camelCaseToDash(model.getName());
            // replace dash with underscore and upper case
            uniqueName = uniqueName.replace('-', '_');
            uniqueName = uniqueName.toUpperCase(Locale.ENGLISH);
            Integer suffix = dups.get(uniqueName);
            if (suffix == null) {
                dups.put(uniqueName, 1);
            } else {
                dups.put(uniqueName, suffix + 1);
                uniqueName = uniqueName + "_" + suffix;
            }
            model.uniqueName = uniqueName;
        }
        return result;
    }

    protected List<ApiMethodModel> processResults(List<ApiMethodModel> result) {
        return result;
    }

    protected Class<?> forName(String className) {
        try {
            return forName(className, classLoader);
        } catch (ClassNotFoundException e1) {
            throw new IllegalArgumentException("Error loading class " + className);
        }
    }

    public static Class<?> forName(String className, ClassLoader classLoader) throws ClassNotFoundException {
        Class<?> result = null;
        try {
            // lookup primitive types first
            result = PRIMITIVE_TYPES.get(className);
            if (result == null) {
                result = Class.forName(className, true, classLoader);
            }
        } catch (ClassNotFoundException e) {
            // check if array type
            if (className.endsWith("[]")) {
                final int firstDim = className.indexOf('[');
                final int nDimensions = (className.length() - firstDim) / 2;
                result = Array.newInstance(forName(className.substring(0, firstDim), classLoader), new int[nDimensions])
                        .getClass();
            } else if (className.indexOf('.') != -1) {
                // try replacing last '.' with $ to look for inner classes
                String innerClass = className;
                while (result == null && innerClass.indexOf('.') != -1) {
                    int endIndex = innerClass.lastIndexOf('.');
                    innerClass = innerClass.substring(0, endIndex) + "$" + innerClass.substring(endIndex + 1);
                    try {
                        result = Class.forName(innerClass, true, classLoader);
                    } catch (ClassNotFoundException ignore) {
                        // ignore
                    }
                }
            }
            if (result == null && !className.startsWith(JAVA_LANG)) {
                // try loading from default Java package java.lang
                try {
                    result = forName(JAVA_LANG + className, classLoader);
                } catch (ClassNotFoundException ignore) {
                    // ignore
                }
            }
        }

        if (result == null) {
            throw new ClassNotFoundException(className);
        }

        return result;
    }

    public static final class ApiMethodModel {
        private final String name;
        private final Class<?> resultType;
        private final List<ApiMethodArg> arguments;
        private final Method method;
        private final String description;
        private final String signature;

        private String uniqueName;

        ApiMethodModel(String name, Class<?> resultType, List<ApiMethodArg> arguments, Method method,
                       String description, String signature) {
            this.name = name;
            this.resultType = resultType;
            this.arguments = arguments;
            this.method = method;
            this.description = description;
            this.signature = signature;
        }

        ApiMethodModel(String uniqueName, String name, Class<?> resultType, List<ApiMethodArg> arguments,
                       Method method, String description, String signature) {
            this.name = name;
            this.uniqueName = uniqueName;
            this.resultType = resultType;
            this.arguments = arguments;
            this.method = method;
            this.description = description;
            this.signature = signature;
        }

        public String getUniqueName() {
            return uniqueName;
        }

        public String getName() {
            return name;
        }

        public Class<?> getResultType() {
            return resultType;
        }

        public Method getMethod() {
            return method;
        }

        public List<ApiMethodArg> getArguments() {
            return arguments;
        }

        public String getDescription() {
            return description;
        }

        public String getSignature() {
            return signature;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append(resultType.getName()).append(" ");
            builder.append(name).append("(");
            for (ApiMethodArg argument : arguments) {
                builder.append(argument.getType().getCanonicalName()).append(" ");
                builder.append(argument.getName()).append(", ");
            }
            if (!arguments.isEmpty()) {
                builder.delete(builder.length() - 2, builder.length());
            }
            builder.append(");");
            return builder.toString();
        }
    }
}
